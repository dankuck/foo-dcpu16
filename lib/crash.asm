; Ends the program
; Supports Notch's favorite crash form, an 
; infinite loop, if notch_crash is defined.
; Otherwise, it issues an undefined instruction
;
; Author: while1dan

.ifndef crash

:crash
	.ifdef notch_crash
		SET PC, crash
	.else
		.dw 0x0000	; causes an exception on the foo-dcpu16 emulator. likely to do so for a long time.
	.end

.end
