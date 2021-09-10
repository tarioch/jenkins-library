class DockerbuildParams {
    String registry = ''
    String repository
    String tag = ''
}

def call(Map params) {
    doCall(params as DockerbuildParams)
}

def doCall(DockerbuildParams params) {
    def label = "kaniko-${UUID.randomUUID().toString()}"

    podTemplate(name: 'kaniko', label: label, yaml: """
kind: Pod
metadata:
  name: kaniko
spec:
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.6.0-debug
    imagePullPolicy: Always
    command:
    - /busybox/cat
    tty: true
    """
    ) {
        node(label) {
            def commitHash
            def registry = params.registry
            def repository = params.repository
            def tag = params.tag
            stage('Checkout') {
                 def scmVars = checkout scm
                 commitHash = scmVars.GIT_COMMIT
            }
            stage('Build') {
                if (registry == '') {
                    registry = 'registry.tario.org'
                }
                if (tag == '') {
                    tag = "${BRANCH_NAME}-${currentBuild.startTimeInMillis}-${commitHash}"
                }
                def image = "${registry}/${repository}:${tag}"
                def additionalTag = ''
                if (BRANCH_NAME == 'master') {
                    additionalTag = "--destination=${registry}/${repository}:latest"
                }

                echo image
                container(name: 'kaniko', shell: '/busybox/sh') {
                    withEnv(['PATH+EXTRA=/busybox:/kaniko']) {
                        ansiColor('xterm') {
                            sh """#!/busybox/sh
                            /kaniko/executor -f `pwd`/Dockerfile -c `pwd` --cache=true --snapshotMode=redo --use-new-run --destination=${image} ${additionalTag}
                            """
                        }
                    }
                }
            }
        }
    }
}
