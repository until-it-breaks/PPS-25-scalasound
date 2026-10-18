package scalasound.state

import scalasound.domain.AudioTrack

trait StateStore {
  def get: AppState
  def update(f: AppState => AppState): AppState
}

case class AppState(track: Option[AudioTrack] = None, isPlaying: Boolean = false)