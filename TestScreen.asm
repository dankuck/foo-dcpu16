; Test writing a string from data to the screen memory

:start	SET X, string_to_print
	JSR print
	SET PC, crash

:print	; (X = string pointer)

	SET B, 0x8000

:printloop
	SET A, [X]
	IFE A, 0
		SET PC, POP
	SET [B], A
	ADD X, 1
	ADD B, 1
	SET PC, printloop
	
:crash
	DAT 0x0 ; causes chip to actually crash

:string_to_print

	DAT "Holy crap it worked."
