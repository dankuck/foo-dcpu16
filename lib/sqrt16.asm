; A shortcut to using ViEtArmis's sqrt32 for 16 bit integers

.include "lib/sqrt32.asm"

.ifndef sqrt16

.ifndef sqrt{ :sqrt }

:sqrt16 ; (pass in via A, results in A)
	SET B, 0
	SET PC, sqrt32

.end
