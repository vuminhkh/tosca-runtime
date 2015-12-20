package com.toscaruntime.compiler

import com.toscaruntime.util.FileUtil
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.PlaySpec

class AbstractSpec extends PlaySpec with LazyLogging with BeforeAndAfter {

  before {
    FileUtil.delete(TestConstant.TEST_DATA_PATH)
  }
}
