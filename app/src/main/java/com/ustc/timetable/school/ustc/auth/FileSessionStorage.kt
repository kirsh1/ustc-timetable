package com.ustc.timetable.school.ustc.auth

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class FileSessionStorage(context: Context) : SessionStorage {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    override fun read(): ByteArray? = try {
        file.openRead().use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }

    override fun write(data: ByteArray?) {
        if (data == null) {
            file.delete()
            return
        }

        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(data)
            output.flush()
            file.finishWrite(output)
        } catch (cause: Throwable) {
            if (output != null) file.failWrite(output)
            throw cause
        }
    }

    private companion object {
        const val FILE_NAME: String = "session.bin"
    }
}
