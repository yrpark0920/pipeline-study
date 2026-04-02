def readProFileList(project_name, targetEnv){

    def props = [:]
    echo "TARGET_ENV :: ${targetEnv}"
    echo "====== Properties 읽어오기 ======"
    sh 'env > properties/env_list.txt' //env.getEnvironment() 쓰면 권한 필요해서 이렇게 우회
    props = readProperties file: 'properties/env_list.txt'
    props = readProperties file: 'properties/base.properties', defaults: props
    props = readProperties file: "properties/${targetEnv}.properties", defaults: props
    props = readProperties file: "vals/${project_name}/${targetEnv}.properties", defaults: props

    props = this.mappingValues(props, "\${", "}")

    return props
} // readProFileList()

def mappingValues(props, sTxt, eTxt){
    def newProps = [:]

    for (entry in props){
        def key = entry.key.trim()
        def value = entry.value.trim()

        while(value.contains(sTxt)){
            def sIdx = value.indexOf(sTxt)
            def eIdx = value.indexOf(eTxt)
            def newKey = value.substring(sIdx+2,eIdx) //${가 두 글자라서 +2
            def newValue = props[newKey]
            
            value = value.substring(0,sIdx)+newValue+value.substring(eIdx+1)
        } 
        newProps.put(key,value)
    } //for문 

    return newProps
} 

def fetchSource(){
    if(env.BUILD_ENABLE_YN == "Y"){
        stage('Clone'){
            dir("project"){
                this.gitCheckout(env.project_git_url, env.git_pull_branch, env.credentialsId)
            }
        }
    }
} //fetchSource()

def gitCheckout (git_url, git_branch, credentialsId){
    checkout([
        scm: scmGit(
            branches: [
                [name: "${git_branch}"]
            ],
            extensions: [],
            userRemoteConfigs: [
                [
                    credentialsId: "${credentialsId}",
                    url: "${git_url}"
                ]
            ]
        ),
        poll: false 
    ])
} //gitCheckout()


def buildWithNpm() {
    if(env.BUILD_REACT_YN == "Y") {
        echo "React 프로젝트로 판단되었습니다."
        stage('NPM Build'){
            dir("project"){
                sh 'ls -al ../'
                sh 'ls -al'
                this._buildWithNpm()
            }
        }
    }

} //buildWithNpm

def _buildWithNpm(){
    sh """
        npm install
        npm run build
        ls -al build/
        """
} //_buildWithNpm

def uploadS3Bucket(){
    if(env.STATIC_UPLOAD_YN == "Y") {
        stage('Push To S3'){
            dir("project"){
                this._uploadS3Bucket(env.s3_upload_bucket)
            }
        }
    }
} //uploadS3Bucket

def _uploadS3Bucket (bucketName) {
        sh """
        aws s3 sync build/ s3://${bucketName}
        """
} //_uploadS3Bucket

def cleanUp(){
    stage('CleanUp'){
        script{
            cleanWs()
            dir("${env.WORKSPACE}@tmp") {
                deleteDir()
            }
        }
    }
} //cleanUp


def build(){
    this.fetchSource()
    this.buildWithNpm()
    this.uploadS3Bucket()

    this.cleanUp()
} //build

return this

