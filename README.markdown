# KillrVideo Integration Tests #

This application will run **integration tests** for the **[KillrVideo]** server (whatever the chosen language of implementation)

## Setting Docker env variables

If you're running on Linux with Docker, just execute the file _getenvirongment.sh_ as explained below.

If you're running Docker4Mac (and not Docker Toolbox with VirtualBox), you need to find manually the current host IP and the HyperKit VM IP:

- type `ifconfig | grep inet`. The HyperKit VM IP is on this list as well as your host IP
- don't use `127.0.0.1` as your host IP since it's a loopback address
- type `export KILLRVIDEO_DOCKER_IP=<HyperKit_VM_IP>`
- type `export KILLRVIDEO_HOST_IP=<Host_IP>`

## Running Locally

This application is not MEANT to be run in a stand-alone mode but inside a Docker compose config. However it is still possible to run it as a separated application by following the below steps: 

* First clone the project with `git clone https://github.com/doanduyhai/KillrVideoIT.git`
* Ensure that you have already run the Docker images for KillrVideo (with a `docker-compose ...`) 
* Ensure that you have already run your own implementation of KillrVideo server
* Execute the script _getenvirongment.sh_ to set environment variables with `. ./getenvironment.sh`

> **warning: the first dot (.) is important! It will execute the script in the context of the calling shell**
<br/>

* Create the following folder `/tmp/cucumber-report` so that test HTML report can be generated 
* Run the tests suite with `mvn clean test`


## Running as a Docker image

You can integrate the Docker image of this application inside a `docker-compose.yaml` as follow:

```yaml

 it_test_suite:
    image: doanduyhai/killrvideo_it:v2
    ports:
    # Zeppelin WEB UI port
    - 8080:8080
    # Python HTTP Web Server port
    - 8123:8123
    depends_on:
    - etcd
    - cassandra
    environment:
      KILLRVIDEO_DOCKER_IP: $KILLRVIDEO_DOCKER_IP
      SERVICE_8080_NAME: zeppelin
      SERVICE_8123_NAME: python_web_server
```


## License
Copyright 2016-2017 Duy Hai DOAN

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[Killrvideo]: https://killrvideo.github.io
[cassandra]: http://cassandra.apache.org/
[dse]: http://www.datastax.com/products/datastax-enterprise
[getting-started]: https://killrvideo.github.io/getting-started/
[getting-started-csharp]: https://killrvideo.github.io/docs/languages/c-sharp/
[twitter]: https://twitter.com/doanduyhai
[Achilles Object Mapper]: http://achilles.io
