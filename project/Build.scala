/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.Keys._
import sbt._

object Build extends Build {

  val silhouette = Project(
    id = "play-silhouette",
    base = file("silhouette")
  )

  val silhouetteTestkit = Project(
    id = "play-silhouette-testkit",
    base = file("silhouette-testkit"),
    dependencies = Seq(silhouette)
  )

  val root = Project(
    id = "root",
    base = file("."),
    aggregate = Seq(silhouette, silhouetteTestkit),
    settings = Defaults.coreDefaultSettings ++
      APIDoc.settings ++
      Seq(
        publishLocal := {},
        publishM2 := {},
        publishArtifact := false
      )
  )
}
