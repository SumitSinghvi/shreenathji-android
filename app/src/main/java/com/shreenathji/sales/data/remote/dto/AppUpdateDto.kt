package com.shreenathji.sales.data.remote.dto

data class AppUpdateDto(
    val id: String? = null,
    val version_code: Int = 0,
    val version_name: String = "",
    val apk_url: String = "",
    val is_mandatory: Boolean = false,
    val changelog: String? = null,
    val min_supported_version_code: Int? = null,
    val created_at: String? = null
)
