/*
* Copyright (c) 2009-2018. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.client.Cytomine
import be.cytomine.client.models.*
import be.cytomine.client.collections.*
import com.vividsolutions.jts.io.WKTReader


// Get command line parameters
String host = args[0]
String publicKey = args[1]
String privateKey = args[2]
Long idProject = Long.parseLong(args[3])
Long idSoftware = Long.parseLong(args[4])
Long idAnnotation = Long.parseLong(args[5])
Long idTerm = Long.parseLong(args[6])
Boolean reviewedOnly = Boolean.parseBoolean(args.length > 7 ? args[7] : 'false')

// Establish connection with Cytomine server
Cytomine cytomine = new Cytomine(host, publicKey, privateKey)
def currentUser = cytomine.getCurrentUser()
def runByUI = false
def job
if (!currentUser.get("algo")) {
    // If user connects as a human (CLI execution)
    job = cytomine.addJob(idSoftware, idProject)
    def userJob = cytomine.getUser(job.get("userJob"))
    cytomine = new Cytomine(host, userJob.get("publicKey"), userJob.get("privateKey"))
} else {
    // If the user executes the job through the Cytomine interface
    job = cytomine.getJob(currentUser.get("job"))
    runByUI = true
}

// Publish parameters
if (!runByUI) {
    def softwareParameters = cytomine.getSoftware(idSoftware).get("parameters")
    softwareParameters.each({
        def value = null
        if (it.name == 'cytomine_id_annotation')
            value = idAnnotation
        else if (it.name == 'cytomine_id_term')
            value = idTerm
        else if (it.name == 'reviewed_only')
            value = reviewedOnly

        if (value)
            cytomine.addJobParameter(job.getId(), it.id, value.toString())
    })
}

try {
    cytomine.changeStatus(job.getId(), Cytomine.JobStatus.RUNNING, 0, "Retrieve data")
    Annotation baseAnnotation = cytomine.getAnnotation(idAnnotation)
    Term term = cytomine.getTerm(idTerm)

    cytomine.changeStatus(job.getId(), Cytomine.JobStatus.RUNNING, 10, "Retrieve included annotations in ROI")
    def filters = [:]
    filters.put("project", baseAnnotation.getStr("project"))
    filters.put("image", baseAnnotation.getStr("image"))
    filters.put("term", idTerm as String)
    filters.put("showWKT", "true")
    filters.put("showTerm", "true")
    filters.put("showMeta", "true")
    filters.put("showBasic", "true")
    filters.put("bbox", URLEncoder.encode(baseAnnotation.getStr("location"), "UTF-8"))
    if (reviewedOnly) filters.put("reviewed", "true")
    AnnotationCollection annotations = cytomine.getAnnotations(filters)

    cytomine.changeStatus(job.getId(), Cytomine.JobStatus.RUNNING, 50, "Computing statistics")

    def baseGeometry = new WKTReader().read(baseAnnotation.get('location'))
    def inside = []
    for (int i = 0; i < annotations.size(); i++) {
        inside << new WKTReader().read(annotations.get(i).getStr('location')).intersection(baseGeometry)
    }

    String propertyNumber = "NUMBER_OF_${term.getStr("name").toUpperCase()}"
    addProperty(cytomine, baseAnnotation.getId(), propertyNumber, annotations.size().toString())

    Long area = 0L
    inside.each { area += it.area }
    String propertyArea = "AREA_OF_${term.getStr("name").toUpperCase()}"
    addProperty(cytomine, baseAnnotation.getId(), propertyArea, area + " " + baseAnnotation.getStr("areaUnit"))

    cytomine.changeStatus(job.getId(), Cytomine.JobStatus.SUCCESS, 100, "Finished.")
}
catch (Exception e) {
    println e.toString()
    cytomine.changeStatus(job.getId(), Cytomine.JobStatus.FAILED, 100, "Error: ${e.toString()}" as String)
}


def addProperty(Cytomine cytomine, Long idAnnotation, String key, String value) {
    PropertyCollection properties
    try {
        println "Update property $key for annotation $idAnnotation"
        properties = cytomine.getDomainProperties("annotation", idAnnotation)

        for (int i = 0; i < properties.size(); i++) {
            Property property = properties.get(i)

            try {
                if (property.getStr("key").equals(key)) {
                    cytomine.deleteDomainProperty("annotation", property.getId(), idAnnotation)
                }
            } catch (Exception e) {
                println e.stackTrace
            }
        }
    } catch (Exception e) {
        println e.stackTrace
    }
    cytomine.addDomainProperties("annotation", idAnnotation, key, value)
}