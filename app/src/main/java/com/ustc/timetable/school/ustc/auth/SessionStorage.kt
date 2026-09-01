package com.ustc.timetable.school.ustc.auth

interface SessionStorage {
    fun read(): ByteArray?
    fun write(data: ByteArray?)
}
