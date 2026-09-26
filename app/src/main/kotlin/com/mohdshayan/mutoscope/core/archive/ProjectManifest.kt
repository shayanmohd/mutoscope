package com.mohdshayan.mutoscope.core.archive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * project.json inside a .mutoscope file. Cel images sit next to it as cels/<reel>-<frame>.png;
 * a null entry is a blank frame. Format version 1.
 */
@Serializable
data class ProjectManifest(
    val format: Int = FORMAT,
    val name: String,
    val aspect: String,
    val widthPx: Int,
    val heightPx: Int,
    val fps: Int,
    val paperArgb: Int,
    val reels: List<ReelManifest>,
) {
    companion object {
        const val FORMAT = 1
        const val FILE_NAME = "project.json"
        const val MAX_FRAMES = 240

        /** A real project.json is a few kilobytes; anything past this is damaged or hostile. */
        const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        fun celEntry(reelIndex: Int, frame: Int): String = "cels/$reelIndex-$frame.png"

        private val entryPattern = Regex("^cels/\\d{1,4}-\\d{1,3}\\.png$")

        fun encode(manifest: ProjectManifest): String = json.encodeToString(serializer(), manifest)

        /**
         * Reads project.json from a zip entry without trusting its declared size, so a file that
         * inflates to gigabytes is refused after [limit] bytes instead of filling memory.
         */
        fun readText(input: InputStream, limit: Int = MAX_MANIFEST_BYTES): String {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) throw ManifestException("project.json is too large")
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }

        /** Parses and validates; throws [ManifestException] with a reason a person can act on. */
        fun decode(text: String): ProjectManifest {
            val m = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                throw ManifestException("project.json is not readable")
            }
            validate(m)
            return m
        }

        fun validate(m: ProjectManifest) {
            if (m.format != FORMAT) throw ManifestException("made by a newer version of Mutoscope")
            if (m.widthPx !in 16..4096 || m.heightPx !in 16..4096) throw ManifestException("canvas size is out of range")
            if (m.fps !in 4..30) throw ManifestException("speed is out of range")
            if (m.reels.isEmpty()) throw ManifestException("it has no reels")
            for (r in m.reels) {
                if (r.cels.isEmpty()) throw ManifestException("a reel has no frames")
                if (r.cels.size > MAX_FRAMES) throw ManifestException("a reel has more than $MAX_FRAMES frames")
                if (r.hold !in 1..4) throw ManifestException("a reel has an unknown hold")
                if (!r.opacity.isFinite() || r.opacity !in 0f..1f) throw ManifestException("a reel has an unknown opacity")
                for (entry in r.cels) {
                    if (entry != null && !entryPattern.matches(entry)) throw ManifestException("a frame points outside the file")
                }
            }
        }
    }
}

@Serializable
data class ReelManifest(
    val name: String,
    val hold: Int = 1,
    @SerialName("phaseOffset") val phaseOffset: Int = 0,
    val opacity: Float = 1f,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val kind: String = "DRAWN",
    val includeInExport: Boolean = true,
    val cels: List<String?>,
)

class ManifestException(message: String) : Exception(message)
