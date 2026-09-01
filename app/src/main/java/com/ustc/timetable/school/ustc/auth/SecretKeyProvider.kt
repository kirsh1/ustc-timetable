package com.ustc.timetable.school.ustc.auth

import javax.crypto.SecretKey

interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}
