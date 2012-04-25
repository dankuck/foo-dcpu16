; Test writing a string from data to the screen memory

:start	SET X, string_to_print
	JSR print
	SET X, string_to_print
	JSR print
	SET PC, crash

:print	; (X = string pointer)

	SET B, [cursor_position]

:printloop
	SET A, [X]
	IFE A, 0
		SET PC, endprint
	SET [B], A
	ADD X, 1
	ADD B, 1
	SET PC, printloop
:endprint
	SET [cursor_position], B
	SET PC, POP
	
	
:crash
	DAT 0x0000 ; causes chip to actually crash

:string_to_print
	DAT "Holy crap it worked.", 0x0

:cursor_position
	DAT 0x8000