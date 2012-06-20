if (! window.DCPU){

window.DCPU = function(dcpuer){
	if (typeof dcpuer == 'string')
		dcpuer = document.getElementById(dcpuer);
	this.index = dcpuer.create();
	this.dcpuer = dcpuer;
};
window.DCPU.prototype = {
	peek: function(start, length_or_structure){ 
		var length = typeof length_or_structure == 'number' ? length_or_structure : this.structure_length(length_or_structure);
		var structure = typeof length_or_structure == 'number' ? null : length_or_structure;
		var hex = this.dcpuer.peekHex(this.index, start, length);
		var bytes = eval("[0x" + hex.trim().replace(/\s+/g, ",0x") + "]");
		return this.bytes_to_content(bytes, structure);
	},
	poke: function(start, content, structure){ return this.dcpuer.poke(this.index, start, this.content_to_bytes(content, structure)) },
	compile_and_poke: function(start, code){ return this.dcpuer.compileAndPoke(this.index, start, code) },
	peek_hex: function(start, length){ return this.dcpuer.peekHex(this.index, start, length) },
	poke_hex: function(start, bytes){ return this.dcpuer.pokeHex(this.index, start, bytes) },
	run: function(){ return this.dcpuer.run(this.index) },
	stop: function(){ return this.dcpuer.stop(this.index) },
	is_running: function(){ return this.dcpuer.isRunning(this.index) },
	exception: function(){ return this.dcpuer.exception(this.index) },
	set_speed: function(hertz){ return this.dcpuer.setSpeed(this.index, hertz) },
	toString: function(){ return this.dcpuer.dump(this.index) },
	content_to_bytes: function(content, structure){
		if (typeof content == 'string'){
			var bytes = [];
			for (var i = 0; i < content.length; i++)
				bytes[i] = content.charCodeAt(i);
			return bytes;
		}
		if (typeof content == 'number'){
			return this.number_to_bytes(content);
		}
		if (typeof content == 'object'){
			if (content instanceof Array){
				var bytes = [];
				for (var i = 0; i < content.length; i++){
					var subbytes = this.content_to_bytes(content[i]);
					for (var j = 0; j < subbytes.length; j++)
						bytes.push(subbytes[j]);
				}
				return bytes;
			}
			if (typeof content.toDCPU == 'function')
				return content.toDCPU();
			if (structure){
				var bytes = [];
				for (var i = 0; i < structure.length; i++){
					var name = structure[i][0];
					var length = structure[i][1];
					var type = structure[i][2];
					var start = content[name];
					if (type == 'integer'){
						var subbytes = this.number_to_bytes(start);
						while (subbytes.length < length)
							subbytes.unshift(0);
						while (subbytes.length > length)
							subbytes.shift();
						for (var j = 0; j < length; j++)
							bytes.push(subbytes[j]);
					}
					else if (type == 'string' || type == 'string-trim'){
						start = start + "";
						for (var i = 0; i < length; i++)
							bytes.push(i >= start.length ? 0 : start.charCodeAt(i)); 
					}
					else if (type == 'int[]'){
						for (var i = 0; i < length; i++)
							bytes.push(i >= start.length ? 0 : start[i]);
					}
					else
						throw new Error("Structure data has an invalid type for " + name + ": " + type);
				}
				return bytes;
			}
			else
				throw new Error("Can't convert this thing, need structure data");
		}
		throw new Error("Can't convert this thing");
	},
	number_to_bytes: function(number){
		// did you know that javascript only supports 32 bit integers? Probably smart.
		number = parseInt(number);
		var bytes = [number & 0xFFFF];
		if (number >> 16)
			bytes.unshift((number >> 16) & 0xFFFF);
		return bytes;
	},
	structure_length: function(structure){
		if (typeof structure == 'number')
			return structure;
		var length = 0;
		for (var i = 0; i < structure.length; i++)
			length += structure[i][1];
		return length;
	},
	bytes_to_content: function(java_bytes, structure){
		var bytes = [];
		if (java_bytes instanceof Array)
			bytes = java_bytes;
		else
			for (var i = 0; i < java_bytes.length; i++)
				bytes[i] = java_bytes[i];
		if (! structure)
			return bytes;
		if (typeof structure == 'object' && typeof structure.fromDCPU == 'function')
			return structure.fromDCPU(bytes);
		var content = {};
		for (var i = 0, pos = 0; i < structure.length; i++){
			var name = structure[i][0];
			var length = structure[i][1];
			var type = structure[i][2];
			var start = bytes.slice(pos, length);
			if (type == 'integer'){
				if (length > 2)
					throw new Error("JavaScript cannot handle a " + (16 * length) + " bit number.");
				if (length == 1)
					content[name] = start[0];
				else if (length == 2)
					content[name] = (start[0] << 16) | start[1];
				else // length == 0
					content[name] = 0;
			}
			else if (type == 'string' || type == 'string-trim'){
				var value = "";
				for (var j = 0; j < start.length; j++)
					value += String.fromCharCode(start[j] != 0 ? start[j] : " ");
				if (type == 'string-trim')
					value = value.trim();
				content[name] = value;
			}
			else if (type == 'int[]'){
				content[name] = start;
			}
			else
				throw new Error("Structure data has an invalid type for " + name + ": " + type);
			pos += length;
		}
		return content;
	},
	add_structure: function(name, structure){
		if (name == 'hex')
			throw new Error("'hex' is a reserved structure name");
		this["peek_" + name] = function(location){
			return this.peek(location, structure);
		};
		this["poke_" + name] = function(location, content){
			this.poke(location, content, structure);
		};
	}
};

}
