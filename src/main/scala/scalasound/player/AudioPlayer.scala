package scalasound.player

trait AudioPlayer {
  def play(): Unit
  def pause(): Unit
  def stop(): Unit
}

object DummyAudioPlayer extends AudioPlayer {
  def pause(): Unit =
    println("Pausing")

  def play(): Unit =
    println("Playing")

  def stop(): Unit =
    println("Stopping")
}