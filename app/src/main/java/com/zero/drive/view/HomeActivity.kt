package com.zero.drive.view

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.api.services.drive.model.File
import com.zero.drive.databinding.ActivityHomeBinding
import com.zero.drive.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var homeViewModel: HomeViewModel

    private val permissions = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()
        initHelperAndGetDriveFiles()
        lifecycleScope.launch { attachObservers() }

        binding.fabUpload.setOnClickListener { pickFile() }
    }

    private fun initRecyclerView() {
        runOnUiThread {
            binding.rvFiles.apply {
                adapter = homeViewModel.getFilesAdapter(this@HomeActivity)
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(this@HomeActivity)
            }
        }
    }

    private fun initHelperAndGetDriveFiles() {
        CoroutineScope(Dispatchers.Main).launch {
            val token = intent?.getStringExtra(MainActivity.ACCESS_TOKEN_KEY)
            homeViewModel.initDriveServiceHelper(token.toString()).await()
            homeViewModel.getAllFilesFromDrive()
        }
    }

    private fun attachObservers() {
        homeViewModel.isFilesListFetched.observe(this) {
            if (it != null) {
                homeViewModel.updateFilesData(this@HomeActivity, it)
                binding.tvFileCount.text = "Files: ${it.size}"
            }
        }

        homeViewModel.requestFileDownload.observe(this) {
            if (it != null) {
                downloadFile(it)
            }
        }

        homeViewModel.responseFileDownloadedSuccessfully.observe(this) {
            runOnUiThread {
                Toast.makeText(this@HomeActivity, it.first, Toast.LENGTH_SHORT).show()
                if (it.second != null) {
                    openDownloadedFile(it.second, it.third)
                }
            }
        }

        homeViewModel.responseFileUpload.observe(this) {
            runOnUiThread {
                Toast.makeText(this@HomeActivity, it.first, Toast.LENGTH_SHORT).show()
                if (it.second) {
                    homeViewModel.getAllFilesFromDrive()
                }
            }
        }
    }

    private fun openDownloadedFile(file: java.io.File?, type: String?) {
        val fileUri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // Should match the authorities in manifest
            file!!
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.setDataAndType(fileUri, type)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            //Handle failure
        }
    }

    private fun pickFile() {
        if (hasWritePermissions()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            pickFile.launch(intent)        }
        else requestMultiplePermissions.launch(permissions)
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        } else {
            Log.e("TAG", "File picking cancelled or failed")
        }
    }

    @SuppressLint("Range")
    private fun handleSelectedFile(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))

                val file = homeViewModel.uriToFile(this@HomeActivity, uri, displayName)
                Log.d("TAG", "handleSelectedFile: ${file.path}")
                homeViewModel.uploadFile(file, Files.probeContentType(file.toPath()))
            }
        }
    }

    private fun downloadFile(file: File) {
        if (hasWritePermissions()) {
            homeViewModel.downloadFile(file)
        }
        else {
            requestMultiplePermissions.launch(permissions)
        }
    }

    private fun hasWritePermissions(): Boolean {
        return (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.containsValue(false)) {
            showRationale()
        }
        else {
            //Permission granted
        }
    }

    private fun showRationale() {
        AlertDialog.Builder(this)
            .setTitle("Storage permission required")
            .setPositiveButton("Allow") { dialog, _ ->
                dialog.dismiss()
                openSettings()
            }
            .setNegativeButton("Exit") { _, _ -> finishAffinity() }
            .show()
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }
}