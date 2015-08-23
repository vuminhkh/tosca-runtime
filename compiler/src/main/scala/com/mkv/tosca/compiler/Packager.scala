package com.mkv.tosca.compiler

import java.nio.file.{StandardCopyOption, FileSystem, Files, Path}

import com.google.common.io.Closeables
import com.mkv.util.FileUtil
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer

/**
 * Produce package from individual compiled csar
 * @author Minh Khang VU
 */
object Packager extends LazyLogging {

  def copyDependencies(dependencyPath: Path, outputPath: Path) = {
    val archiveFolder = dependencyPath.resolve(Constant.ARCHIVE_FOLDER)
    if (Files.exists(archiveFolder)) {
      FileUtil.copy(archiveFolder, outputPath.resolve(Constant.ARCHIVE_FOLDER), StandardCopyOption.REPLACE_EXISTING)
    }
    val typesFolder = dependencyPath.resolve(Constant.TYPES_FOLDER)
    if (Files.exists(typesFolder)) {
      FileUtil.copy(typesFolder, outputPath.resolve(Constant.TYPES_FOLDER), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  def produceDeployablePackage(compiledTopology: Path, compiledLibraries: List[Path], outputPath: Path) = {
    var recipePath = outputPath
    val fileSystemsToClose = ListBuffer[FileSystem]()
    if (!Files.isDirectory(outputPath)) {
      recipePath = FileUtil.createZipFileSystem(outputPath)
      fileSystemsToClose += recipePath.getFileSystem
    }
    var compiledTopologyPath = compiledTopology
    if (Files.isRegularFile(compiledTopology)) {
      compiledTopologyPath = FileUtil.createZipFileSystem(compiledTopology)
      fileSystemsToClose += compiledTopologyPath.getFileSystem
    }
    // Load dependencies
    val compiledLibrariesPaths = compiledLibraries.map { compiledLibraryPath =>
      if (!Files.isDirectory(compiledLibraryPath)) {
        val zipPath = FileUtil.createZipFileSystem(compiledLibraryPath)
        fileSystemsToClose += zipPath.getFileSystem
        zipPath
      } else {
        compiledLibraryPath
      }
    }
    try {
      FileUtil.copy(compiledTopologyPath.resolve(Constant.DEPLOYMENT_FOLDER), recipePath.resolve(Constant.DEPLOYMENT_FOLDER), StandardCopyOption.REPLACE_EXISTING)
      copyDependencies(compiledTopologyPath, recipePath)
      compiledLibrariesPaths.foreach { compiledLibrary =>
        copyDependencies(compiledLibrary, recipePath)
      }
    } finally {
      fileSystemsToClose.foreach { fileSystem =>
        Closeables.close(fileSystem, true)
      }
    }
  }
}
