#!/bin/bash

# Define RELEASE=1 before running to build a release.
APP="fishingly"
RELEASE=1

echo "Downloading and updating compiler..."
if git clone https://github.com/Feodor0090/j2me_compiler.git 2>/dev/null; then
  echo "Done."
else
  echo "Already downloaded."
fi
cd j2me_compiler || exit 1
git pull
cd ..

# Manifest
cp "Application Descriptor" manifest.mf
echo -n "Commit: " >> manifest.mf
git rev-parse --short HEAD >> manifest.mf 2>/dev/null || echo "unknown" >> manifest.mf

WORK_DIR=$(dirname "$0")
cd "${WORK_DIR}" || exit 1

# Extract version from Application Descriptor and fix jar URL
VERSION=$(grep "MIDlet-Version:" "Application Descriptor" | sed 's/MIDlet-Version: //')
if [ -z "${VERSION}" ]; then
  echo "Unable to determine version from Application Descriptor" >&2
  exit 1
fi
sed -i "s|^MIDlet-Jar-URL: .*|MIDlet-Jar-URL: ${APP}-${VERSION}.jar|" manifest.mf

mkdir -p jar

# STATIC VARS
JAVA_HOME="./j2me_compiler/jdk1.6.0_45"
WTK_HOME="./j2me_compiler/WTK2.5.2"
PROGUARD="./j2me_compiler/proguard/bin/proguard.sh"
RES="res"
MANIFEST="manifest.mf"
PATHSEP=":"

if [ -n "${JAVA_HOME}" ] && [ -d "${JAVA_HOME}" ]; then
  JAVAC="${JAVA_HOME}/bin/javac"
  JAR="${JAVA_HOME}/bin/jar"
else
  JAVAC="javac"
  JAR="jar"
fi

# DYNAMIC VARS
LIB_DIR="${WTK_HOME}/lib"
CLDCAPI="${LIB_DIR}/cldcapi11.jar"
MIDPAPI="${LIB_DIR}/midpapi20.jar"
PREVERIFY="${WTK_HOME}/bin/preverify"

# Надежное формирование CLASSPATH через массивы bash (защита от пробелов в путях)
shopt -s nullglob
JARS=("${LIB_DIR}"/*.jar)
CLASSPATH=$(IFS=${PATHSEP}; echo "${JARS[*]}")
shopt -u nullglob

# Extract version from Application Descriptor
VERSION=$(grep "MIDlet-Version:" "Application Descriptor" | sed 's/MIDlet-Version: //')

# ACTION
echo "Working on ${APP} (version: ${VERSION})"
pwd
echo "Creating or cleaning directories..."
mkdir -p ./tmpclasses ./classes
rm -rf ./tmpclasses/* ./classes/*

echo "Compiling source files..."
# Собираем все .java файлы
find ./src -name '*.java' > sources.list

"${JAVAC}" \
    -bootclasspath "${CLDCAPI}${PATHSEP}${MIDPAPI}" \
    -source 1.3 \
    -target 1.3 \
    -d ./tmpclasses \
    -classpath "./tmpclasses${PATHSEP}${CLASSPATH}" \
    @sources.list
    
if [ $? -eq 0 ]; then
  echo "Compilation ok!"
else
  echo "Compilation failed!"
  exit 1
fi

echo "Preverifying class files..."
"${PREVERIFY}" \
    -classpath "${CLDCAPI}${PATHSEP}${MIDPAPI}${PATHSEP}${CLASSPATH}${PATHSEP}./tmpclasses" \
    -d ./classes \
    ./tmpclasses
    
if [ $? -eq 0 ]; then
  echo "Preverify ok!"
else
  echo "Preverify failed!"
  exit 1
fi

echo "Jaring preverified class files..."
JAR_FILE="${APP}-${VERSION}.jar"
"${JAR}" cmf "${MANIFEST}" "${JAR_FILE}" -C ./classes .

if [ -d "${RES}" ]; then
  "${JAR}" uf "${JAR_FILE}" -C "${RES}" .
fi

echo "Build done! ./${JAR_FILE}"

echo "Optimizing ${APP}..."
chmod +x "${PROGUARD}"

PROGUARD_OUT_JAR="${APP}-${VERSION}_obf.jar"

# Формируем конфиг ProGuard
cat proguard.cfg > cf.cfg
echo "-injars ./${JAR_FILE}" >> cf.cfg
echo "-outjar ./${PROGUARD_OUT_JAR}" >> cf.cfg
echo "-printseeds ./${APP}_obf_seeds.txt" >> cf.cfg
echo "-printmapping ./${APP}_obf_map.txt" >> cf.cfg
echo "-libraryjars ${CLASSPATH}" >> cf.cfg

"${PROGUARD}" @cf.cfg

# Перемещаем jar файлы
mv "${JAR_FILE}" jar/ 2>/dev/null || true
mv "${PROGUARD_OUT_JAR}" jar/ 2>/dev/null || true

# Генерируем JAD файл для обфусцированного (релизного) JAR
JAD_FILE="${APP}-${VERSION}.jad"
JAR_SIZE=$(stat -c%s "jar/${PROGUARD_OUT_JAR}" 2>/dev/null || wc -c < "jar/${PROGUARD_OUT_JAR}")
cp "${MANIFEST}" "jar/${JAD_FILE}"
echo "MIDlet-Jar-Size: ${JAR_SIZE}" >> "jar/${JAD_FILE}"
echo "MIDlet-Jar-URL: ${PROGUARD_OUT_JAR}" >> "jar/${JAD_FILE}"

rm ./sources.list ./manifest.mf ./cf.cfg ./${APP}_obf_map.txt ./${APP}_obf_seeds.txt 2>/dev/null || true
rm -rf ./tmpclasses ./classes 2>/dev/null || true

echo "Done! Check the /jar folder."