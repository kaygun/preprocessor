#!/usr/bin/python

import os
import sys
import io 


with open(sys.argv[1],"r") as infile:
    lines = infile.readlines()

flag = False
command = ''
block={'results': True, 'echo': True}

outfile = open(sys.argv[2],"w")
for x in lines:
    if(x[0:3]=="```"):
      if(not(flag)):
         command = ''
         rest = x[3:]
         exec("block.update("+rest+")")
         if(block['echo']):
           outfile.write("```python\n")
      else:
         if(block['echo']):
            outfile.write("```\n")
            if(block['results']):
                outfile.write("\n```python\n")
         res = io.StringIO()
         sys.stdout = res
         exec(command)
         sys.stdout = sys.__stdout__
         if(block['results']):
            outfile.write(res.getvalue())
            if(block['echo']):
               outfile.write('```\n')
      flag = not(flag)
      outfile.flush()
      continue

    if(flag): 
      command = command + x
      if(block['echo']):
        outfile.write(x)
    else:
      u = x.strip('\n').split("`")
      N = len(u)
      if(N == 1):
         outfile.write(x)
      else:
         for i in range(N):
             if(i%2 == 0):
               outfile.write(u[i])
             else:
               exec("outfile.write(" + u[i] + ",end='')")
      outfile.flush()

outfile.close()
