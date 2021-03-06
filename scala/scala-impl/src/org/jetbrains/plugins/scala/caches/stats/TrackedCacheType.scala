package org.jetbrains.plugins.scala.caches.stats

trait TrackedCacheType {
  type Cache

  def id: String
  def name: String
  def alwaysTrack: Boolean
  def capabilities: CacheCapabilities[Cache]
  def tracked: List[Cache]
  def cachedEntityCount: Int
  def clear(): Unit
}
