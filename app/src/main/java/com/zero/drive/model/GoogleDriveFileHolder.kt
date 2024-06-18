package com.zero.drive.model

/**
 * Represents a Google Drive file uploaded/downloaded/retrieved.
 *
 * @property id Unique identifier for every google drive file.
 * @property name Name of the file.
 */
data class GoogleDriveFileHolder(
    var id: String? = null,
    var name: String? = null
)