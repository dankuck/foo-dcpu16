; Reading characters from the keyboard
; by Markus Persson
;
; From: http://pastebin.com/raw.php?i=aJSkRMyC
; Edited for safety.
;
; NOTE: The macro will not work correctly more than once in the foo-dcpu16 Assembler.
; Plans to fix that are upcoming

.ifndef nextkey

.include "lib/sugar.asm"

#macro nextkey(target) {
	push(i)
	set i,[keypointer]
	add i,0x9000
	set target,[i]
	ife target,0
		jmp(end)
	
	set [i],0
	add [keypointer], 1
	and [keypointer], 0xf
:end
	pop(i)
}

:keypointer
dat 0

:nextkey ; (return: A = next key)
	set B, [keypointer]
	add B, 0x9000
	set A, [B]
	ife A, 0
		SET PC, POP
	
	set [B], 0
	add [keypointer], 1
	and [keypointer], 0xf
	SET PC, POP

.end
