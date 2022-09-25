import sbt._
import sbt.Keys._

/**
  * Plugin that enables and configures the SBT remote caching features.
  * Automatically enabled for all projects using JvmPlugin.
  * It's important that this is enabled for all projects, even the root project, as you otherwise
  * get some confusing errors, as discussed on gitter: https://gitter.im/sbt/sbt?at=6037b65acfd9b375cd55a02f
  *
  * Some helpful resources:
  *  - https://www.reddit.com/r/scala/comments/w6it6c/sbtscalatest_library_or_plugin_that_only_reruns/
  *  - https://reibitto.github.io/blog/remote-caching-with-sbt-and-s3/
  */
object BuildCachePlugin extends AutoPlugin {

  // The trigger indicates that the plugin is automatically enabled when all requirements are met.
  override val requires = plugins.JvmPlugin
  override val trigger = allRequirements

  // This build cache is currently only available to the AWS IAM user used by Elastiknn's CI.
  // If you don't have permissions, running pullRemoteCache will fail gracefully with warnings.
  private val s3RemoteCacheResolver =
    "S3 Remote Cache" at "s3://elastiknn-sbt-build-cache.s3-us-east-1.amazonaws.com/remote_cache"

  override lazy val projectSettings = Seq(
    remoteCacheResolvers += s3RemoteCacheResolver,
    pushRemoteCacheTo := Some(s3RemoteCacheResolver),
    Compile / pushRemoteCacheConfiguration ~= (_.withOverwrite(true)),
    Test / pushRemoteCacheConfiguration ~= (_.withOverwrite(true))
  )
}
