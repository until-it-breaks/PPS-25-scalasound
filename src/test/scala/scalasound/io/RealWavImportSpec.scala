package scalasound.io

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.security.MessageDigest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.StereoTrack
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.collection.mutable
import scala.util.Using

/** Verifies the import of two real WAV files of different sizes.
 *
 *  Reference values were obtained using Python's `wave` module reading the raw
 *  PCM data (both files: stereo, 44100 Hz, 16-bit signed PCM). For each channel,
 *  the SHA-256 of its samples as little-endian int16 was computed. The listed spot
 *  samples (start, middle, end) are checked individually to give readable failures.
 */
class RealWavImportSpec extends AnyFlatSpec with Matchers:

  private case class Fixture(
      resource: String,
      frames: Int,
      leftSha256: String,
      rightSha256: String,
      spots: Vector[ReferenceSpot]
  )
  private case class ReferenceSpot(
    frameIndex: Int,
    leftSample: Int,
    rightSample: Int
  )
  private val fixtures = Vector(
    Fixture(
      "/file_example_WAV_2MG.wav",
      526_073,
      "0c2c795c9898b37e401abf638b452fa55f245f3dee833d3e7ecfacf65154f4f4",
      "3994479633ab6436f734a0c7b4d40eaff60944e0863a2c706b513ed01e9ba7b3",
      Vector(
        ReferenceSpot(0, -1, 0), ReferenceSpot(1, 3, 0), ReferenceSpot(1000, -4151, -4253),
        ReferenceSpot(263_036, -1, 0), ReferenceSpot(526_071, 0, -2), ReferenceSpot(526_072, 2, 1))
    ),
    Fixture(
      "/file_example_WAV_5MG.wav",
      1_322_253,
      "d1e7060f734ccfb364a4902c3b6671317196b757b8ae2fc886f62749a7b7260a",
      "5c799583eb56c163c52b9c7d8054dfcbca4bba23d48ddb6e0eb8c1a34416be49",
      Vector(ReferenceSpot(0, 0, 0), ReferenceSpot(1, 1, 0), ReferenceSpot(1000, -4154, -4256),
        ReferenceSpot(661_126, -1717, -739), ReferenceSpot(1_322_251, -1, 1), ReferenceSpot(1_322_252, 1, -1))
    )
  )
  private val sampleRate = 44100
  private val resolvedResourcePaths = mutable.Map.empty[String, String]

  private def resourcePath(resource: String): String =
    resolvedResourcePaths.synchronized {
      resolvedResourcePaths.getOrElseUpdate(resource, {
        val url = Option(getClass.getResource(resource)).getOrElse(
          fail(s"test resource not found on the classpath: $resource")
        )

        if url.getProtocol == "file" then
          Paths.get(url.toURI).toString
        else
          val tempFile = Files.createTempFile("scalasound-wav-", ".wav")

          try
            Using.resource(url.openStream()) { input =>
              Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            tempFile.toFile.deleteOnExit()
            tempFile.toAbsolutePath.toString
          catch
            case e: Exception =>
              Files.deleteIfExists(tempFile)
              throw e
      })
    }

  private def loadStereo(resource: String): StereoTrack =
    WavAudioReader.load(resourcePath(resource)) match
      case Right(track: StereoTrack) =>
        track
      case other =>
        fail(s"expected a StereoTrack for $resource but got ${other.getClass.getSimpleName}: $other")

  private def fromNormalisedFloatToInt16(samples: Vector[Float]): Vector[Int] =
    samples.map(s => Math.round(s * 32768f))

  private def sha256OfInt16LittleEndian(values: Vector[Int]): String =
    val buffer = ByteBuffer.allocate(values.length * 2).order(LITTLE_ENDIAN)
    values.foreach(v => buffer.putShort(v.toShort))
    MessageDigest.getInstance("SHA-256").digest(buffer.array()).map("%02x".format(_)).mkString

  for fixture <- fixtures do
    val name = s"The WAV file ${fixture.resource.drop(1)}"

    name should "be imported as a stereo track with the sample rate of the source" in {
      val track = loadStereo(fixture.resource)

      track.sampleRate shouldBe sampleRate
    }

    name should "contain every frame of the source and a duration consistent with them" in {
      val track = loadStereo(fixture.resource)

      track.frameCount shouldBe fixture.frames.toLong
      track.chunks.map(_.left.length.toLong).sum shouldBe fixture.frames.toLong
      track.chunks.map(_.right.length.toLong).sum shouldBe fixture.frames.toLong
      Math.abs(track.durationInSeconds - fixture.frames.toDouble / sampleRate) < 1e-9 shouldBe true
    }

    name should "have well formed chunks: aligned channels, only the last one shorter" in {
      val chunks = loadStereo(fixture.resource).chunks
      chunks should not be empty

      val chunkSize = chunks.head.left.length

      chunks.forall(c =>
        c.left.nonEmpty && c.left.length == c.right.length
      ) shouldBe true

      chunks.init.forall(_.left.length == chunkSize) shouldBe true
      chunks.last.left.length should (be > 0 and be <= chunkSize)
    }

    name should "match the reference samples at the start, middle and end of the track" in {
      val track = loadStereo(fixture.resource)
      val left = fromNormalisedFloatToInt16(track.chunks.flatMap(_.left))
      val right = fromNormalisedFloatToInt16(track.chunks.flatMap(_.right))

      fixture.spots.foreach { spot =>
        (left(spot.frameIndex), right(spot.frameIndex)) shouldBe
          (spot.leftSample, spot.rightSample)
      }
    }

    name should "match the reference SHA-256 of the whole left and right sample sequences" in {
      val track = loadStereo(fixture.resource)

      sha256OfInt16LittleEndian(fromNormalisedFloatToInt16(track.chunks.flatMap(_.left))) shouldBe fixture.leftSha256
      sha256OfInt16LittleEndian(fromNormalisedFloatToInt16(track.chunks.flatMap(_.right))) shouldBe fixture.rightSha256
    }

    name should "only contain normalized samples in [-1.0, 1.0)" in {
      val track = loadStereo(fixture.resource)

      track.chunks.forall(c => (c.left ++ c.right).forall(s => s >= -1.0f && s < 1.0f)) shouldBe true
    }
