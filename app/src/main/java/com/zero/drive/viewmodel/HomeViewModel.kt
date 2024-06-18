package com.zero.drive.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.zero.drive.DriveServiceHelper
import com.zero.drive.adapter.FilesAdapter
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Date

class HomeViewModel: ViewModel() {

    private var filesAdapter: FilesAdapter? = null

    private lateinit var driveServiceHelper: DriveServiceHelper

    val responseFileUpload = MutableLiveData<Pair<String, Boolean>>()
    val responseFileDownloadedSuccessfully = MutableLiveData<Triple<String, java.io.File?, String?>>()
    val requestFileDownload = MutableLiveData<File>()
    val isFilesListFetched = MutableLiveData<List<File?>>()

    //Initialize the adapter for recycler view to display files list
    fun getFilesAdapter(context: Context): FilesAdapter {
        return filesAdapter ?: FilesAdapter(context, mutableListOf()) { pos, file ->
            //Handle download button clicks - Requests download to ui by passing the selected file
            requestFileDownload.postValue(file)
        }.also { filesAdapter = it }
    }

    /**
     * Requests files list update to the adapter
     * @param files New and updated files list to be shown on UI
     */
    fun updateFilesData(context: Context, files: List<File?>) {
        getFilesAdapter(context).updateData(files)
    }

    /**
     * Initializes DriveApiHelper which can later be used to perform read/write operations on the Drive
     * @param accessToken Access token retrieved from the authorization during sign in
     * to construct the Drive Api
     */
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

    // To retrieve all files created by authorized acc or shared with authorized acc
    fun getAllFilesFromDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = driveServiceHelper.listDriveFiles()
                isFilesListFetched.postValue(files)
                //Files are posted to ui where it'll be updated and notify the adapter
                Log.d("TAG", "getAllFilesFromDrive: ${files?.size}")
            } catch (e: IOException) {
                //Handle IO exceptions while retrieving files from Drive
            }
        }
    }

    /**
     * To download the file selected
     * @param driveFile Drive File with metadata such as name and id to start download using DriveServiceHelper
     */
    fun downloadFile(driveFile: File) {
        val file = getFile(driveFile.name, driveFile.fileExtension)
        if (file != null) {
            driveServiceHelper.downloadFile(file, driveFile.id, driveFile.mimeType)
                .addOnSuccessListener {
                    Log.i("TAG", "Downloaded the file")
                    val fileSize = file?.length()?.div(1024)

                    Log.i("TAG", "file Size :$fileSize")
                    Log.i("TAG", "file Path :" + file?.absolutePath)

                    //Success message along with file path sent to ui via callback to open up file immediately after download
                    responseFileDownloadedSuccessfully.postValue(Triple("Saved in: ${file?.path}", file, driveFile.mimeType))
                }
                .addOnFailureListener { e ->
                    Log.i("TAG", "Failed to Download the file, Exception :" + e.message)
                    //Handle IO exceptions while downloading files from Drive, any error is thrown to show in the UI
                    responseFileDownloadedSuccessfully.postValue(Triple("Error: ${e.message}", null, null))
                }
        }
    }

    /**
     * To start upload the selected file using DriveServiceHelper
     * @param file File is the chosen file to be uploaded
     * @param type Mime Type of the chosen file
     */
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

    /**
     * Uri to File - The temp file will be in cache
     * @param uri Uri of the selected file
     * @param fileName File name to be cached
     */
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

    //Concatenates directory, file name and extension to returns the newly created file
    private fun getFile(fileName: String, fileExtension: String?): java.io.File? {
        val directory: java.io.File? = getDirectory()
        val timestamp = Date.from(Instant.now()).time.toString()

        Log.d("TAG", "getFile: $fileName $fileExtension")
        if (directory != null) {
            val file = if (fileExtension != null) "$timestamp.$fileExtension" else timestamp
            return java.io.File(directory, file)
        }

        return null
    }

    //Get public directory based on Android version and restrictions
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