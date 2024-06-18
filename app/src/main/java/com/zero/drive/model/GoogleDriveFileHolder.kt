package com.zero.drive.model

import com.google.api.client.util.DateTime

data class GoogleDriveFileHolder(
    var id: String? = null,
    var name: String? = null,
    var modifiedTime: DateTime? = null,
    var size: Long? = null,
    var createdTime: DateTime? = null,
    var starred: Boolean? = null
)