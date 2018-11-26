# * Copyright (c) 2009-2018. Authors: see NOTICE file.
# *
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# *
# *      http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.

FROM cytomineuliege/software-groovy-base:v1.4.2

ADD jts-1.13.jar /lib/jts-1.13.jar
ADD run.groovy /app/run.groovy

ENTRYPOINT ["groovy", "-cp", "/lib/cytomine-java-client.jar:/lib/jts-1.13.jar", "/app/run.groovy"]
