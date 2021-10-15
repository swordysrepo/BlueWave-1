''' this script is for recreating HTML files run in github actions  '''

import os
from os.path import dirname

# use directory containing main.html
mainLocation = os.path.dirname(os.getcwd())+"/compiledProjectDirectory/web"
# set directory for rebuilt main.html
# newLocation = os.path.dirname(os.getcwd())+"/compiledProjectDirectory"



itemsToRemove = [
'src="app', 
"<!-- Admin Modules -->",
 "<!-- Graph Stuff -->",
  "<!-- Misc -->",
 "<!-- Dashboard Creator -->",
 "<!-- Dashboards -->"
 ]

with open(mainLocation + "/main.html", "r") as f:
    lines = f.readlines()
with open(mainLocation + "/main.html", "w+") as f:
    for line in lines:
        writeLine = True
        for removeWord in itemsToRemove:
            if not removeWord in line:
                pass
            else:
                writeLine = False
        if writeLine == False:
            # the line contained one of the lines that's not allowed - do nothing
            pass
        else:
            # add the line to the new HTML file
            f.write(line)

        # add the BlueWave file reference just below the kartographia reference
        if '<script src="lib/kartographia/kartographia.js" type="text/javascript"></script>' in line:
            
            f.write('\n\n<!-- BlueWave-->\n')
            f.write('<script src="bluewave.js" type="text/javascript"></script>\n')

