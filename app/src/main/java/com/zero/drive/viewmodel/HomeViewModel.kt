package com.zero.drive.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.zero.drive.DriveServiceHelper
import com.zero.drive.adapter.FilesAdapter
import com.zero.drive.model.GoogleDriveFileHolder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.Instant
import java.util.Date

class HomeViewModel: ViewModel() {

    private var filesAdapter: FilesAdapter? = null

    private lateinit var driveServiceHelper: DriveServiceHelper

    val responseFileUpload = MutableLiveData<Pair<String, Boolean>>()
    val responseFileDownloadedSuccessfully = MutableLiveData<Triple<String, java.io.File?, String?>>()
    val requestFileDownload = MutableLiveData<File>()
    val isFilesListFetched = MutableLiveData<List<File?>>()

    fun getFilesAdapter(context: Context): FilesAdapter {
        return filesAdapter ?: FilesAdapter(context, mutableListOf()) { pos, file ->
            requestFileDownload.postValue(file)
        }.also { filesAdapter = it }
    }

    fun updateFilesData(context: Context, files: List<File?>) {
        getFilesAdapter(context).updateData(files)
    }

    fun initDriveServiceHelper(accessToken: String): Deferred<Unit> {
        return viewModelScope.async(Dispatchers.IO) {
            val credentials = GoogleCredentials.create(
                AccessToken(accessToken, null))

            val service = Drive.Builder(NetHttpTransport(), GsonFactory(),
                HttpCredentialsAdapter(credentials)).setApplicationName("Drive Teachmint")
                .build()

            driveServiceHelper = DriveServiceHelper(service)
        }
    }

    fun getAllFilesFromDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = driveServiceHelper.listDriveFiles()
            isFilesListFetched.postValue(files)
            Log.d("TAG", "getAllFilesFromDrive: ${files?.size}")
        }
    }

    fun downloadFile(driveFile: File) {
        val file = getFile(driveFile.name, driveFile.fileExtension)
        if (file != null) {
            driveServiceHelper.downloadFile(file, driveFile.id, driveFile.mimeType)
                .addOnSuccessListener {
                    Log.i("TAG", "Downloaded the file")
                    val fileSize = file?.length()?.div(1024)

                    Log.i("TAG", "file Size :$fileSize")
                    Log.i("TAG", "file Path :" + file?.absolutePath)

                    responseFileDownloadedSuccessfully.postValue(Triple("Saved in: ${file?.path}", file, driveFile.mimeType))
                }
                .addOnFailureListener { e ->
                    Log.i("TAG", "Failed to Download the file, Exception :" + e.message)
                    responseFileDownloadedSuccessfully.postValue(Triple("Error: ${e.message}", null, null))
                }
        }
    }

    fun uploadFile(file: java.io.File, type: String) {
        Log.d("TAG", "uploadFile: $type")
        driveServiceHelper.uploadFile(file, type, null)
            ?.addOnSuccessListener {
                Log.i(
                    "TAG",
                    "Successfully Uploaded. File Id :" + it?.id
                )
                responseFileUpload.postValue(Pair("File uploaded successfully", true))
            }
            ?.addOnFailureListener { e ->
                Log.i("TAG", "Failed to Upload. File Id :" + e.message)
                responseFileUpload.postValue(Pair("Uploaded failed: ${e.message}", false))
            }
    }

    fun uriToFile(context: Context, uri: Uri, fileName: String?): java.io.File {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val outputFile = java.io.File(context.cacheDir, fileName)

        inputStream?.let {
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(4 * 1024) // buffer size
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }

        return outputFile
    }

    private fun getFile(fileName: String, fileExtension: String?): java.io.File? {
        val directory: java.io.File? = getDirectory()
        val timestamp = Date.from(Instant.now()).time.toString()

        Log.d("TAG", "getFile: $fileName $fileExtension")
        if (directory != null) {
            val file = if (fileExtension != null) "$timestamp.$fileExtension" else timestamp
            return getFilePath(directory, file)
        }

        return null
    }

    private fun getFilePath(directory: java.io.File, fileNameWithExtension: String): java.io.File {
        return java.io.File(directory, fileNameWithExtension)
    }

    private fun getDirectory(): java.io.File? {
        var directory: java.io.File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString() + "/My Drive"
            );
        } else {
            java.io.File(Environment.getExternalStorageDirectory().toString() + "/My Drive");
        }

        if (!directory?.exists()!!) {
            // Make it, if it doesn't exit
            val success: Boolean = directory.mkdirs()
            if (!success) {
                directory = null
            }
        }
        Log.d("TAG", "getDirectory: $directory")
        return directory
    }
}