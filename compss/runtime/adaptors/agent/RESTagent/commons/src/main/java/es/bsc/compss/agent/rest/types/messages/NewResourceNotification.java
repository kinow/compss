/*         
 *  Copyright 2002-2018 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.agent.rest.types.messages;

import es.bsc.compss.agent.rest.types.Resource;
import es.bsc.compss.types.resources.MethodResourceDescription;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "newResource")
public class NewResourceNotification {

    private Resource resource;

    public NewResourceNotification() {
    }

    public NewResourceNotification(String name, MethodResourceDescription mrd, String adaptor, Object resourcesConf, Object projectConf) {
        this.resource = Resource.createResource(name, mrd, adaptor, resourcesConf, projectConf);
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    @XmlElements({
        @XmlElement(name = "externalResource", type = Resource.ExternalAdaptorResource.class, required = false),
        @XmlElement(name = "nioResource", type = Resource.NIOAdaptorResource.class, required = false),})
    public Resource getResource() {
        return resource;
    }

}