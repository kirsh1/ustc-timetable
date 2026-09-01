package com.ustc.timetable.school.ustc.parser

object UstcFixtureLoader {
    fun load(name: String): String {
        require(
            name.isNotBlank() &&
                !name.contains("..") &&
                !name.contains('/') &&
                !name.contains('\\'),
        ) { "fixture name must be a basename" }

        val stream = checkNotNull(
            UstcFixtureLoader::class.java.getResourceAsStream("/fixtures/ustc/$name"),
        ) { "missing fixture $name" }
        return stream.use { it.readBytes().decodeToString() }
    }
}
