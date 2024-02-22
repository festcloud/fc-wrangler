pipeline {
    environment {
        // Define environment variables
        LOCATION = 'europe-west4'
        PROJECT = 'gcp-lab-datafusion-poc'
        CUSTOMER = 'fest'
        ARTIFACT = 'wrangler-transform'
        Green = '\033[0;32m'
        IPurple = '\033[0;95m'
        BRed='\033[1;31m'
        On_Green='\033[42m'
        On_Cyan='\033[46m'
        Color_Off = '\033[0m'
    }
    agent any

    triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ref', value: '$.ref']
            ],
            causeString: 'Triggered on $ref',
            token: '',
            tokenCredentialId: 'webhook-token',
            printContributedVariables: true,
            printPostContent: true,
            regexpFilterText: '$ref',
            regexpFilterExpression: 'refs/heads/fcmain'
        )
    }

    stages {
        stage('Setup parameters') {
            steps {
                script {
                    properties([
                        parameters([
                            string(
                                defaultValue: 'fcmain',
                                name: 'Branch',
                                trim: true
                            ),
                            choice(
                                choices: ['dev'],
                                name: 'DEPLOY_TO'
                            )
                        ])
                    ])
                }
            }
        }
        stage('Prepare') {
            agent {
                kubernetes {
                    yaml kubernetesPodYaml('maven')
                }
            }
            steps {
                script {
                    env.fusionUrl = "gcp-dp-${DEPLOY_TO}-dtfu-instance-gcp-dataplatform-${DEPLOY_TO}-dot-euw4.datafusion.googleusercontent.com"
                    checkoutAndStash()
                    ansiColor('xterm') {
                        dir('fc-wrangler') {
                            def buildAndDeploy = buildAndDeploy()
                        }
                    }
                }
            }
        }
    }
}

// Kubernetes pod definition for Maven and GCloud
def kubernetesPodYaml(String containerName) {
    return """
        apiVersion: v1
        kind: Pod
        spec:
          containers:
          - name: maven
            image: 'maven:3.8.1-jdk-8'
            command:
            - sleep
            args:
            - 99d
          - name: gcloud
            image: 'google/cloud-sdk:latest'
            command:
            - sleep
            args:
            - 99d
    """
}


// Checkout and stash the repository contents
def checkoutAndStash() {
    withCredentials([string(credentialsId: 'github-token', variable: 'GIT_TOKEN')]) {
        checkout scm: [
            $class: 'GitSCM',
            branches: [[name: "*/${Branch}"]],
            userRemoteConfigs: [[url: "https://$GIT_TOKEN@github.com/festcloud/fc-wrangler.git"]]
        ]
        stash includes: '**/*', name: 'repo-contents'
    }
}

// Build and deploy for a specific folder
def buildAndDeploy() {
    stage("Test ${ARTIFACT} plugins") {
        agent {
            kubernetes {
                yaml kubernetesPodYaml('maven') // Build stage uses Maven
            }
        }
        container('maven') {
            testFolder()
        }
    }
    stage("Build ${ARTIFACT} plugins") {
        agent {
            kubernetes {
                yaml kubernetesPodYaml('maven') // Build stage uses Maven
            }
        }
        container('maven') {
            unstash 'repo-contents'
                buildFolder()
        }
    }
    stage("Deploy ${ARTIFACT} plugins") {
        agent {
            kubernetes {
                yaml kubernetesPodYaml('gcloud') // Deploy stage uses GCloud
            }
        }
        container('gcloud') {
            unstash 'builded'
            deployFolder(fusionUrl)
        }
    }
}

def testFolder() {
    unstash 'repo-contents'
    ansiColor('xterm') {
        sh "mvn clean test"
    }
}

// Build logic for a specific folder
def buildFolder() {
    ansiColor('xterm') {
        sh 'pwd'
        sh 'ls -la'
        // Maven build commands
        sh "mvn install -DskipTests -Drat.skip=true -Dcheckstyle.skip -P-submodules"
        sh """
        set +x
        echo "\${IPurple}========================================================"
        echo "\${IPurple}===========  \${Green}Building \${On_Cyan}\${BRed}${ARTIFACT} plugins\${Color_Off} plugin..... \${IPurple}==========="
        echo "\${IPurple}========================================================\${Color_Off}"
        set -x
        mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout > version.txt
        set +x
        echo "\${IPurple}====================================================="
        echo "\${IPurple}===========  \${On_Cyan}\${BRed}${ARTIFACT}\${Color_Off}\${Green} version : \$(cat version.txt) \${IPurple}==========="
        echo "\${IPurple}=====================================================\${Color_Off}"
        set -x
        chown -R 1000:1000 .
        ls -la
        ls -la wrangler-transform/
        """
        stash includes: '*/*', name: "builded"
    }
}

// Deploy logic for a specific folder
def deployFolder(String fusionUrl) {
    unstash "builded"
    confirmDeployment()
    ansiColor('xterm') {
        withCredentials([file(credentialsId: 'sa-key', variable: 'SA_KEY')]) {
            // GCloud deploy commands
            sh "gcloud auth activate-service-account --key-file=\$SA_KEY"
            sh """
            apt update
            apt install jq -y
            export GOOGLE_APPLICATION_CREDENTIALS=\$SA_KEY
            set +x
            echo "\${Green}Deploying to env\${Color_Off}:"
            echo "\${On_Cyan}\${BRed}${DEPLOY_TO}\${Color_Off}"
            echo "\${Green}Data Fusion URL\${Color_Off}:"
            echo "\${On_Cyan}\${BRed}\${fusionUrl}\${Color_Off}"
            echo "\${Green}Plugin:\${Color_Off}"
            echo "\${On_Cyan}\${BRed}${ARTIFACT} plugins\${Color_Off}"
            set -x

            jq '.properties' ${ARTIFACT}/target/${ARTIFACT}-\$(cat version.txt).json > ${ARTIFACT}/target/properties.json

            set +x
            echo "\${Green}Deploying plugin ${ARTIFACT} plugins....\${Color_Off}"

            curl -X POST \\
            -H "Authorization: Bearer \$(gcloud auth print-access-token)" \\
            -H "Artifact-Extends: system:cdap-data-pipeline[6.8.0-SNAPSHOT,7.0.0-SNAPSHOT)/system:cdap-data-streams[6.8.0-SNAPSHOT,7.0.0-SNAPSHOT)" \\
            -H "Artifact-Version: \$(cat version.txt)" \\
            --data-binary @${ARTIFACT}/target/${ARTIFACT}-\$(cat version.txt).jar \\
            \${fusionUrl}/api/v3/namespaces/\$DEPLOY_TO/artifacts/${ARTIFACT}

            echo "\${Green}Deployed plugin ${ARTIFACT} plugins\${Color_Off}"
            #Deploy widget to datafusion
            echo "\${Green}Deploying plugin properties for ${ARTIFACT} plugins....\${Color_Off}"

            curl -X PUT \\
            -H "Authorization: Bearer \$(gcloud auth print-access-token)" \\
            --data-binary @${ARTIFACT}/target/properties.json \\
            \${fusionUrl}/api/v3/namespaces/\$DEPLOY_TO/artifacts/${ARTIFACT}/versions/\$(cat version.txt)/properties/

            echo "\${Green}Deployed plugin properties for ${ARTIFACT} plugins\${Color_Off}"

            set -x
            """
        }
    }
}

// Confirm deployment with user input
def confirmDeployment() {
    timeout(time: 15, unit: 'MINUTES') {
        input message: "Do you want to approve the deployment of ${ARTIFACT}?", ok: 'Yes'
    }
}
