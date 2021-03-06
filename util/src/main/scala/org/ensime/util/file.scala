// Copyright (C) 2015 ENSIME Authors
// License: GPL 3.0
package org.ensime.util

import com.google.common.base.Charsets
import java.io.{ File => JFile, _ }
import com.google.common.io.Files

import Predef.{ any2stringadd => _, _ }
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Decorate `java.io.File` with functionality from common utility
 * packages, which would otherwise be verbose/ugly to call directly.
 *
 * Its nicer to put conveniences for working with `File` here
 * instead of using static accessors from J2SE or Guava.
 */
package object file {
  type File = JFile

  /**
   * Convenience for creating `File`s (which we do a lot), but has the
   * caveat that static methods on `java.io.File` can no longer be
   * accessed, so it must be imported like:
   *
   *   `java.io.{ File => JFile }`
   */
  def File(name: String): File = new File(name)

  implicit val DefaultCharset: Charset = Charset.defaultCharset()

  /**
   * WARNING: do not create symbolic links in the temporary directory
   * or the cleanup script will exit the sandbox and start deleting
   * other files.
   */
  def withTempDir[T](a: File => T): T = {
    // sadly not able to provide a prefix. If we really need the
    // support we could re-implement the Guava method.
    val dir = Files.createTempDir().canon
    try a(dir)
    finally dir.tree.reverse.foreach(_.delete())
  }

  def withTempFile[T](a: File => T): T = {
    val file = JFile.createTempFile("ensime-", ".tmp").canon
    try a(file)
    finally file.delete()
  }

  implicit class RichFile(val file: File) extends AnyVal {

    def /(sub: String): File = new File(file, sub)

    def isScala: Boolean = file.getName.toLowerCase.endsWith(".scala")
    def isJava: Boolean = file.getName.toLowerCase.endsWith(".java")
    def isClassfile: Boolean = file.getName.toLowerCase.endsWith(".class")

    def parts: List[String] =
      file.getPath.split(
        Pattern.quote(JFile.separator)
      ).toList.filterNot(Set("", "."))

    def outputStream(): OutputStream = new FileOutputStream(file)

    def createWithParents(): Unit = {
      Files.createParentDirs(file)
      file.createNewFile()
    }

    def readLines()(implicit cs: Charset): List[String] = {
      import collection.JavaConversions._
      Files.readLines(file, cs).toList
    }

    def writeLines(lines: List[String])(implicit cs: Charset): Unit = {
      Files.write(lines.mkString("", "\n", "\n"), file, cs)
    }

    def writeString(contents: String)(implicit cs: Charset): Unit = {
      Files.write(contents, file, cs)
    }

    def readString()(implicit cs: Charset): String = {
      Files.toString(file, cs)
    }

    /**
     * @return the file and its descendent family tree (if it is a directory).
     */
    def tree: Stream[File] = {
      import collection.JavaConversions._
      file #:: Files.fileTreeTraverser().breadthFirstTraversal(file).toStream
    }

    /**
     * Helps to resolve ambiguity surrounding files in symbolically
     * linked directories, which are common on operating systems that
     * use a symbolically linked temporary directory (OS X I'm looking
     * at you).
     *
     * @return the canonical form of `file`, falling back to the absolute file.
     */
    def canon =
      try file.getCanonicalFile
      catch {
        case t: Throwable => file.getAbsoluteFile
      }

  }

}
