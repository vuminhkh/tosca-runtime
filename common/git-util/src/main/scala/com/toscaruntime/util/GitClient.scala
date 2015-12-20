package com.toscaruntime.util

import java.nio.file.{Files, Path}

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
  * Git client to easily clone git project
  *
  * @author Minh Khang VU
  */
object GitClient {

  /**
    * Clone a git repository, throw exception if the target directory already exists
    * @param url url of the repository to clone
    * @param target target path
    * @param branch branch to clone
    * @param user git user
    * @param password git password
    * @return cloned git repository
    */
  def clone(url: String, target: Path, branch: String = "master", user: String = "", password: String = ""): Git = {
    Files.createDirectories(target)
    val command = Git.cloneRepository.setURI(url).setBranch(branch).setDirectory(target.toFile)
    if (user.nonEmpty && password.nonEmpty) {
      command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password))
    }
    command.call
  }
}
