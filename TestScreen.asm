; Writes a string to the screen in a loop, 
; scrolling when appropriate and pausing 
; 1/2 second between prints

:start	
:main_loop
	SET A, string_to_print
	JSR print
	SET A, 500
	JSR pause
	SET PC, main_loop

.include "lib/pause.asm"
.include "lib/print.asm"
.include "lib/crash.asm"

:string_to_print
	DAT p"Holy crap it worked! "
