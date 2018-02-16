# jogl-hello-vr

Jogl porting of the original hello vr sample, plus several enhances, like:

- general cleaning
- improved readability by moving code in their corresponding class (Scene, line controllers, model render, distorsion)
- fixed vertex layout mismatch between vertex array setup and shader attribute input
- left nothing implicit to the driver (buffer bindings)


![Sample](http://imgur.com/HoIX75N.png)

Steps:

- clone
- sync Gradle
- run


If it is stuttering, disable v-sync and try to play with `glFlush` and `glFinish` inside `display()`
