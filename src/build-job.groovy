node('', {
    def version = "1.${env.BUILD_NUMBER}"

    stage 'Checkout and Build'

    git url: 'https://github.com/joebew42/daily-activity-log-to-rss.git'

    sh "git tag ${version}"

    createVirtualEnv 'env'
    executeIn 'env', 'pip install -r requirements.txt'
    executeIn 'env', './manage.py test'
    executeIn 'env', './manage.py integration-test'

    stage 'Build Docker Image'

    def name = "daily2rss"
    def repository = "joebew42/${name}"
    def tag = version

    def image = docker.build("${repository}:${tag}", '.')
    def containerName = name + '-' + tag

    stage 'Acceptance Test'

    image.withRun("-P --name ${containerName}") { c ->
        def ports = containerPorts(containerName)
        sh "curl -I http://localhost:${ports['5000']}/rss/?url=http://joebew42.github.io/events.xml"
    }

    stage 'Tag Docker Image as Latest'

    sh "docker tag -f ${repository}:${tag} ${repository}:latest"
})

def createVirtualEnv(String name) {
    sh "virtualenv ${name}"
}

def executeIn(String environment, String script) {
    sh "source ${environment}/bin/activate && " + script
}

def containerPorts(String containerName) {
    def ports = [:]
    def networkPorts = "docker inspect --format='{{.NetworkSettings.Ports}}' ${containerName}".execute().text
    (networkPorts =~ /map\[((\d+)\/tcp:\[\{0.0.0.0 (\d+)\}\])\]/).each { group ->
        ports[group[2]] = group[3]
    }
    return ports
}

