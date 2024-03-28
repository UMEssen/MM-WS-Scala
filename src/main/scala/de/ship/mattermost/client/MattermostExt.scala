package de.ship.mattermost.client

import akka.http.scaladsl.model.StatusCodes
import de.ship.mattermost.client.model.Attachments
import net.bis5.mattermost.client4.{MattermostClient, Pager}
import net.bis5.mattermost.model.{Post, PostList, PostPatch, PostType}

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object MattermostExt {

  implicit class MattermostClientExt(mmc: MattermostClient) {

    def getAllPosts(channelId: String): Seq[Post] = {
      val buffer = mutable.Buffer.empty[Post]

      var page = 0
      var posts: PostList = null
      do {
        posts = mmc.getPostsForChannel(channelId, Pager.of(page, 100)).readEntity()
        posts.getPosts.asScala.foreach {
          case (id, post) => buffer.append(post)
        }
        page += 1
      } while (posts.size() > 0)

      buffer.toSeq
    }

    def post(channelId: String,
             message: String,
             pinned: Boolean = false,
             postType: PostType = PostType.DEFAULT,
             props: Map[String, AnyRef] = Map.empty,
             attachments: Option[Attachments] = None,
             fileIds: Seq[String] = Seq.empty): Try[String] = {

      val post = new Post()
      post.setChannelId(channelId)
      post.setMessage(message)
      post.setPinned(pinned)
      post.setType(postType)

      (props, attachments) match {
        case (p, Some(a)) if p.nonEmpty =>
          post.setProps((p ++ Map("attachments" -> a.forMM)).asJava)
        case (p, Some(a)) if p.isEmpty =>
          post.setProps(Map("attachments" -> a.forMM).map(t => (t._1, t._2.asInstanceOf[AnyRef])).asJava)
        case (p, None) if p.nonEmpty =>
          post.setProps(p.asJava)
        case _ => // do nothing
      }

      if (fileIds.nonEmpty) post.setFileIds(fileIds.asJava)
      mmc.createPost(post) match {
        case r if r.getRawResponse.getStatus != StatusCodes.Created.intValue =>
          Failure(new RuntimeException("Error creating post: " + r.getRawResponse))
        case r =>
          Success(r.readEntity().getId)
      }
    }

    def patchPost(postId: String,
                  message: Option[String] = None,
                  pinned: Option[Boolean] = None,
                  fileIds: Seq[String] = Seq.empty,
                  props: Map[String, String] = Map.empty): Try[String] = {

      val patch = new PostPatch

      message.foreach(patch.setMessage)
      pinned.foreach(patch.setPinned)
      if (fileIds.nonEmpty) patch.setFileIds(fileIds.asJava)
      if (props.nonEmpty) patch.setProps(props.asJava)

      mmc.patchPost(postId, patch) match {
        case r if r.getRawResponse.getStatus != StatusCodes.OK.intValue =>
          Failure(new RuntimeException(s"Error patching post '$postId': ${r.getRawResponse}"))
        case r =>
          Success(r.readEntity().getId)
      }
    }

  }

}
