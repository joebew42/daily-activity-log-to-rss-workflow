
String basePath = 'daily-activity-log'
String checkoutAndBuild = 'checkout-and-build'
String dockerImageBuild = 'docker-image-build'
String dockerRunContainer = 'docker-run-container'
String acceptanceTest = 'acceptance-test'

folder(basePath) {
    description 'Daily Activity Log To RSS pipeline'
}

job("$basePath/$checkoutAndBuild") {
    scm {
        git {
            remote {
                url('https://github.com/joebew42/daily-activity-log-to-rss.git')
            }
            createTag(false)
        }
    }
    triggers {
        scm('H/15 * * * *')
    }
    steps {
        shell('git tag 1.${BUILD_NUMBER}')
        virtualenv {
            name('venv')
            command('pip install -r requirements.txt')
        }
        virtualenv {
            name('venv')
            command('./manage.py test')
        }
        virtualenv {
            name('venv')
            command('./manage.py integration-test')
        }
        virtualenv {
            name('venv')
            clear()
        }
        publishers {
            downstreamParameterized {
                trigger("$basePath/$dockerImageBuild") {
                    condition('SUCCESS')
                    parameters {
                        predefinedProp('gitTag', '1.$BUILD_NUMBER')
                        predefinedProp('workspaceDirectory', "${JENKINS_HOME}/jobs/${basePath}/jobs/${checkoutAndBuild}/workspace")
                    }
                }
            }
        }
    }
}

job("$basePath/$dockerImageBuild") {
    parameters {
        stringParam('gitTag', '', 'The git tag')
        stringParam('workspaceDirectory', '', 'The workspace where source code is')
    }
    customWorkspace('$workspaceDirectory')
    steps {
        shell('git checkout tags/$gitTag')
        shell('docker build -t joebew42/daily2rss:$gitTag .')
    }
    publishers {
        downstreamParameterized {
            trigger("$basePath/$dockerRunContainer") {
                condition('SUCCESS')
                parameters {
                    predefinedProp('repository', 'joebew42/daily2rss')
                    predefinedProp('tag', '$gitTag')
                }
            }
        }
    }
}

job("$basePath/$dockerRunContainer") {
    parameters {
        stringParam('repository', '', 'The docker image repository (e.g. my_repo/name)')
        stringParam('tag', '', 'The docker image repository tag (e.g. latest, 1.7, etc)')
    }
    steps {
        shell('docker run -d -P --name daily2rss-$tag $repository:$tag')
        shell('export CONTAINER_PORT=$(docker inspect --format=\'{{range $p, $conf := .NetworkSettings.Ports}}{{(index $conf 0).HostPort}}{{end}}\' daily2rss-$tag)')
    }
    publishers {
        downstreamParameterized {
            trigger("$basePath/$acceptanceTest") {
                condition('SUCCESS')
                parameters {
                    currentBuild()
                    predefinedProp('serviceEndpointURL', 'http://localhost:$CONTAINER_PORT')
                }
            }
        }
    }
}

job("$basePath/$acceptanceTest") {
    parameters {
        stringParam('repository', '', 'The docker image repository (e.g. my_repo/name)')
        stringParam('tag', '', 'The docker image repository tag (e.g. latest, 1.7, etc)')
        stringParam('serviceEndpointURL', '', 'The service endpoint URL to perform test (e.g. http://localhost:8765)')
    }
    steps {
        httpRequest('$serviceEndpointURL/rss/?url=http://joebew42.github.io/events.xml') {
            httpMode('GET')
            returnCodeBuildRelevant()
            logResponseBody()
        }
    }
}
