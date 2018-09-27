import os
import sys

WRAPPER_CLASS = "EllaWrapper"
PACKAGE_PATH = "com/apposcopy/ella/runtime"
SOURCE_DIR = "./runtime/src/" + PACKAGE_PATH
OUTPUT_DIR = "./bin/ella-wrappers"
MAX_COUNT = 20

os.system("rm -rf %s" % OUTPUT_DIR)
os.system("mkdir -p %s" % OUTPUT_DIR)

with open(os.path.join(SOURCE_DIR, WRAPPER_CLASS + ".java")) as f:
    wrapper_proto = f.read()

for i in range(1, MAX_COUNT + 1):
    id = WRAPPER_CLASS + str(i)
    fn = os.path.join(OUTPUT_DIR, id + ".java")
    with open(fn, 'w') as f:
        f.write(wrapper_proto.replace(WRAPPER_CLASS, id))
    os.system("javac -d %s %s" % (OUTPUT_DIR, fn))
    os.system("cd %s && dx --dex --output %s %s" % (OUTPUT_DIR, id + ".dex",
                                                    os.path.join(PACKAGE_PATH, id + ".class")))
    # break