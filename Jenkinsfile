pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'RUN_OWASP',
            defaultValue: false,
            description: 'Run OWASP Dependency Check (slow on first run — requires NVD_API_KEY env var)'
        )
    }

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Enforcer') {
            steps {
                sh 'mvn --batch-mode enforcer:enforce'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn --batch-mode test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Static Analysis') {
            steps {
                sh 'mvn --batch-mode checkstyle:check pmd:check'
            }
        }

        stage('Dependency Security') {
            when {
                expression { params.RUN_OWASP }
            }
            environment {
                NVD_API_KEY = "${env.NVD_API_KEY ?: ''}"
            }
            steps {
                sh 'mvn --batch-mode dependency-check:check -DnvdApiKey=${NVD_API_KEY}'
            }
        }

        stage('SonarQube') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn --batch-mode sonar:sonar'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn --batch-mode package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }
    }

    post {
        failure {
            echo 'Pipeline failed — check stage logs for violations'
        }
        success {
            echo 'All quality gates passed. Artifact ready.'
        }
    }
}
