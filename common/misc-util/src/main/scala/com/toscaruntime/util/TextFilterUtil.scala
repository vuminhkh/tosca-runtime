package com.toscaruntime.util

import java.io.{BufferedOutputStream, InputStream, PrintWriter}
import java.nio.file.{Files, Path}

import scala.io.Source
import scala.util.matching.Regex.Match

object TextFilterUtil {
  val pattern = """((?:\\?)\$\{.+?\})""".r

  def replacer(props: Map[String, String]) = (matchData: Match) => {
    matchData.matched match {
      case text if text.startsWith("\\") => Some("""\$\{%s\}""" format text.substring(3, text.length - 1))
      case text => props.get(text.substring(2, text.length - 1))
    }
  }

  /**
    * Filter the given line and replace all occurrence
    *
    * @param line           the line to replace
    * @param replaceContext the context map, key is the text to match, every occurrence of the key will be replaced by the corresponding value
    * @return the line filtered
    */
  def filter(line: String, replaceContext: Map[String, String]) = pattern.replaceSomeIn(line, replacer(replaceContext))

  /**
    * Filter the input file, replace all occurrence and output the filtered text at output
    *
    * @param inputStream    the input as stream
    * @param output         the output file
    * @param replaceContext the context map, key is the text to match, every occurrence of the key will be replaced by the corresponding value
    */
  def filterStream(inputStream: InputStream, output: Path, replaceContext: Map[String, String]) = {
    val writer = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(output)))
    try {
      Source.fromInputStream(inputStream).getLines().foreach(line => {
        writer.println(filter(line, replaceContext))
      })
    } finally {
      writer.close()
    }
  }
}
