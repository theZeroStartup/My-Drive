package com.zero.drive

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.zero.drive.model.GoogleDriveFileHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.Date
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.annotation.Nullable

class DriveServiceHelper(private val mDriveService: Drive? = null) {

    private val mExecutor: Executor = Executors.newSingleThreadExecutor()

    fun downloadFile(targetFile: java.io.File, fileId: String?, mimeType: String?): Task<Void?> {
        return Tasks.call<Void?>(mExecutor) {
            // Retrieve the metadata as a File object.
            Log.d("TAG", "downloadFile: $mimeType")
            val timestamp = Date.from(Instant.now()).time.toString()
            when (mimeType) {
                "application/vnd.google-apps.document" -> {
                    exportGoogleDocs(fileId.toString(), mimeType, timestamp.plus(".docx"))
                    null
                }
                "application/vnd.google-apps.spreadsheet" -> {
                    exportGoogleDocs(fileId.toString(), mimeType, timestamp.plus(".xlsx"))
                    null
                }
                else -> {
                    val outputStream: OutputStream = FileOutputStream(targetFile)
                    mDriveService?.files()?.get(fileId)?.executeMediaAndDownloadTo(outputStream)
                    null
                }
            }
        }
    }

    private fun exportGoogleDocs(fileId: String, mimeType: String, fileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mDriveService?.files()?.export(fileId, mimeType)?.executeMediaAndDownloadTo(FileOutputStream(fileName))

                Log.i("TAG", "File exported")
            } catch (e: IOException) {
                Log.e("TAG", "Error exporting file", e)
            } catch (e: GoogleAuthIOException) {
                Log.e("TAG", "Authorization error", e)
            }
        }
    }

    // TO LIST FILES
    @Throws(IOException::class)
    fun listDriveFiles(): List<File?>? {
        var result: FileList?
        var pageToken: String? = null
        do {
            result = mDriveService?.files()?.list()
                ?.setSpaces("drive")
                ?.setQ("'me' in owners or sharedWithMe") //Can have mimetype or any other filters
                ?.setFields("nextPageToken, files(id, name, fileExtension, mimeType)") //Returns the specified metadata
                ?.setPageToken(pageToken)
                ?.execute()
            pageToken = result?.nextPageToken
        } while (pageToken != null)
        return result?.files
    }

    // TO UPLOAD A FILE ONTO DRIVE
    fun uploadFile(
        localFile: java.io.File,
        mimeType: String?, @Nullable folderId: String?
    ): Task<GoogleDriveFileHolder?>? {
        return Tasks.call(mExecutor) { // Retrieve the metadata as a File object.
            val root: List<String> = if (folderId == null) {
                listOf("root")
            } else {
                listOf(folderId)
            }
            val metadata: File = File()
                .setParents(root)
                .setMimeType(mimeType)
                .setName(localFile.name)
            val fileContent = FileContent(mimeType, localFile)
            val fileMeta: File? = mDriveService?.files()?.create(
                metadata,
                fileContent
            )?.execute()

            //Contains the uploaded file details as how it'll be on the Drive
            val googleDriveFileHolder = GoogleDriveFileHolder()
            googleDriveFileHolder.id = (fileMeta?.id)
            googleDriveFileHolder.name = (fileMeta?.name)
            googleDriveFileHolder
        }
    }
}