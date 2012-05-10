; Unpacks packed data into a new location
; Packed data stores two words in one by ignoring their top 8 bits.
; This is fine for text and the 0xSCA standard includes a flag to 
; pack strings at compile time.
; This function is useful if you want to use a packed string with
; functions that expect unpacked strings.
;
; Author: while1dan

.ifndef unpack

:unpack ; (A = data to unpack, B = size, C = place to unpack)
	SET PUSH, I
	SET PUSH, X
	SET I, 0xFFFF
	:_loop
	SET X, [A]
	SHR X, 8
	SET [C], X
	SET X, [A]
	AND X, 0xFF
	SET [C+1], X
	ADD I, 1
	ADD A, 1
	ADD C, 2
	IFN B, I
		SET PC, _loop
	SET X, POP
	SET I, POP
	SET PC, POP
.end