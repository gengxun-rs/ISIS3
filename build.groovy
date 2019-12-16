// vim: ft=groovy

def isisDataPath = '/isisData/data'

def isisMgrScripts = '/isisData/data/isis3mgr_scripts'

def isisTestDataPath = "/isisData/testData"

def kakaduIncDir = "/isisData/kakadu"

def macOSEnvHash = ""
def macOSMinocondaBin = ""
def macOSMinicondaDir = ""
def condaPath = ""

def isisEnv = [
    "ISIS3DATA=${isisDataPath}",
    "ISIS3TESTDATA=${isisTestDataPath}",
    "ISIS3MGRSCRIPTS=${isisMgrScripts}"
]

def cmakeFlags = [
    "-DJP2KFLAG=ON",
    "-DKAKADU_INCLUDE_DIR=${kakaduIncDir}",
    "-Dpybindings=OFF",
    "-DCMAKE_BUILD_TYPE=RELEASE"
]

def build_ok = true
def errors = []

// Helpers for setting commit status
def getRepoUrl() {
    return sh(script: "git config --get remote.origin.url", returnStdout: true).trim()
}

def getCommitSha() {
    return sh(script: "git rev-parse HEAD", returnStdout: true).trim()
}


def setGitHubBuildStatus(status) {
    def repoUrl = getRepoUrl()
    def commitSha = getCommitSha()

    step([
        $class: 'GitHubCommitStatusSetter',
        reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
        commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
        errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
        statusResultSource: [
          $class: 'ConditionalStatusResultSource',
          results: [
            [$class: 'BetterThanOrEqualBuildResult', result: 'SUCCESS', state: 'SUCCESS', message: status],
            [$class: 'BetterThanOrEqualBuildResult', result: 'FAILURE', state: 'FAILURE', message: status],
            [$class: 'AnyBuildResult', state: 'FAILURE', message: 'Loophole']
          ]
        ]
    ])
}


node("${env.OS.toLowerCase()}") {
    
    environment {
      PATH = "${-> condaPath}/bin:${env.PATH}"
    }

    stage ("Checkout") {
        env.STAGE_STATUS = "Checking out ISIS"
        sh 'git config --global http.sslVerify false'
        checkout scm
        isisEnv.add("ISISROOT=${pwd()}/build")
        cmakeFlags.add("-DCMAKE_INSTALL_PREFIX=${pwd()}/install")
    }

    stage("Create environment") {
        
        env.STAGE_STATUS = "Creating conda environment"
        if (env.OS.toLowerCase() == "mac") {
          macOSEnvHash = sh(script: 'date "+%H:%M:%S:%m" | md5 | tr -d "\n"', returnStdout: true)
          macOSMinicondaDir = "/tmp/$macOSEnvHash"
          macOSMinicondaBin = "$macOSMinicondaDir/bin"
          condaPath = macOSMinicondaDir 

          println(macOSMinicondaDir)
          sh """
            curl -o miniconda.sh  https://repo.continuum.io/miniconda/Miniconda3-latest-MacOSX-x86_64.sh
            bash miniconda.sh -b -p ${macOSMinicondaDir}
            ls ${macOSMinicondaDir}
            
            # Use the conda cache running on the Jenkins host
            # conda config --set channel_alias http://dmz-jenkins.wr.usgs.gov
            export PATH="${macOSMinicondaBin}:${env.PATH}"
            echo $PATH
            which conda
            conda search -c conda-forge ale  
            conda config --set always_yes True
            conda config --set ssl_verify false 
            """
        } else {
         condaPath = "/var/jenkins_home/.conda"
         sh """
            # Use the conda cache running on the Jenkins host
            # conda config --set channel_alias http://dmz-jenkins.wr.usgs.gov
            export PATH="${macOSMinicondaBin}:${env.PATH}"
            echo $PATH
            which conda
            conda search -c conda-forge ale  
            conda config --set always_yes True
            conda config --set ssl_verify false 
            conda create -n isis python=3
        """
        }

        if (env.OS.toLowerCase() == "centos") {
            sh 'conda env update -n isis -f environment_gcc4.yml --prune'
        } else {
          sh """
            export PATH="${macOSMinicondaBin}:${env.PATH}"
            which conda
            conda env update -n isis -f environment.yml --prune
          """
        }
    }  

    withEnv(isisEnv) {
        dir("${env.ISISROOT}") {
            try {
                stage ("Build") {
                    env.STAGE_STATUS = "Building ISIS on ${env.OS}"
                    sh """
                        source activate ${macOSMinicondaDir}/envs/isis
                        echo `ls ../`
                        echo `pwd`
                        conda list
                        cmake -GNinja ${cmakeFlags.join(' ')} ../isis
                        ninja -j4 install
                    """
                }
            }
            catch(e) {
                build_ok = false
                errors.add(env.STAGE_STATUS)
                println e.toString()
            }

            if (build_ok) {

                try{
                    stage("UnitTests") {
                        dir("${env.ISISROOT}") {
                            env.STAGE_STATUS = "Running unit tests on ${env.OS}"
                                sh """
                                    echo $ISIS3TESTDATA
                                    echo $ISIS3DATA

                                    # environment variables
                                    export ISISROOT=${env.ISISROOT}
                                    export ISIS3TESTDATA="/isisData/testData"
                                    export ISIS3DATA="/isisData/data"
                                    export "PATH=`pwd`/../install/bin:${macOSMinicondaDir}/envs/isis/bin:${env.PATH}"

                                    automos -HELP
                                    catlab -HELP
                                    tabledump -HELP

                                    ctest -R _unit_ -j4 -VV
                                """

                        }
                    }
                }
                catch(e) {
                    build_ok = false
                    echo e.toString()
                }
                sh 'source deactivate'

                try{
                    stage("AppTests") {
                        env.STAGE_STATUS = "Running app tests on ${env.OS}"
                        sh """
                            echo $ISIS3TESTDATA
                            echo $ISIS3DATA
                            echo $PATH

                            # environment variables
                            export ISISROOT=${env.ISISROOT}
                            export ISIS3TESTDATA="/isisData/testData"
                            export ISIS3DATA='/isisData/data'
                            export "PATH=`pwd`/../install/bin:${macOSMinicondaDir}/envs/isis/bin:${env.PATH}"

                            catlab -HELP
                            tabledump -HELP

                            ctest -R _app_ -j4 -VV
                        """
                    }
                }
                catch(e) {
                    build_ok = false
                    errors.add(env.STAGE_STATUS)
                    println e.toString()
                }
                sh 'source deactivate'

                try{
                    stage("ModuleTests") {
                        env.STAGE_STATUS = "Running module tests on ${env.OS}"
                        sh """
                            echo $ISIS3TESTDATA
                            echo $ISIS3DATA
                            echo $PATH

                            # environment variables
                            export ISISROOT=${env.ISISROOT}
                            export ISIS3TESTDATA="/isisData/testData"
                            export ISIS3DATA='/isisData/data'
                            export "PATH=`pwd`/../install/bin:${macOSMinicondaDir}/envs/isis/bin:${env.PATH}"

                            catlab -HELP
                            tabledump -HELP

                            ctest -R _module_ -j4 -VV
                        """
                    }
                }
                catch(e) {
                    build_ok = false
                    errors.add(env.STAGE_STATUS)
                    println e.toString()
                }
                sh 'source deactivate'

                try{
                    stage("GTests") {
                        env.STAGE_STATUS = "Running gtests on ${env.OS}"
                        sh """
                            echo $ISIS3TESTDATA
                            echo $ISIS3DATA
                            echo $PATH

                            # environment variables
                            export ISISROOT=${env.ISISROOT}
                            export ISIS3TESTDATA="/isisData/testData"
                            export ISIS3DATA='/isisData/data'
                            export PATH="`pwd`/../install/bin:${macOSMinicondaDir}/envs/isis/bin:${env.PATH}"

                            ctest -R "." -E "(_app_|_unit_|_module_)" -j4 -VV
                        """
                    }
                }
                catch(e) {
                    build_ok = false
                    errors.add(env.STAGE_STATUS)
                    println e.toString()
                }
                sh 'source deactivate'
            }
        }

        if(build_ok) {
            currentBuild.result = "SUCCESS"
        }
        else {
            currentBuild.result = "FAILURE"
            def comment = "Failed during:\n"
            errors.each {
                comment += "- ${it}\n"
            }
            setGitHubBuildStatus(comment)
        }
    }

    stage("Clean Up") {
      env.STAGE_STATUS = "Removing conda environment"
      sh '''
          source deactivate
          conda env remove --name isis
      '''
    }
}
