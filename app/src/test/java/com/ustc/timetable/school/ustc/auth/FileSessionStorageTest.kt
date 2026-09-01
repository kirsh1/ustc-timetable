package com.ustc.timetable.school.ustc.auth

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class FileSessionStorageTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        file = File(context.filesDir, "session.bin")
        file.delete()
        File(context.filesDir, "session.bin.bak").delete()
        File(context.filesDir, "session.bin.new").delete()
    }

    @After fun tearDown() {
        file.delete()
        File(context.filesDir, "session.bin.bak").delete()
        File(context.filesDir, "session.bin.new").delete()
    }

    @Test fun file_storage_roundtrip() {
        val storage = FileSessionStorage(context)
        val bytes = byteArrayOf(1, 2, 3, 4)
        storage.write(bytes)
        assertArrayEquals(bytes, storage.read())
    }

    @Test fun successful_overwrite_replaces_previous_blob() {
        val storage = FileSessionStorage(context)
        storage.write(byteArrayOf(1, 2, 3))
        storage.write(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), storage.read())
    }

    @Test fun file_storage_clear_removes_file() {
        val storage = FileSessionStorage(context)
        storage.write(byteArrayOf(1, 2, 3))
        assertTrue(file.exists())
        storage.write(null)
        assertFalse(file.exists())
        assertNull(storage.read())
    }
}
