// requires wslib & TabbedView quarks
// wslib 2009, revised Nathan Ho 2014

ChannelEQ {
	classvar <>prefsFile;
	var <window;

	var <target, <numChannels, <server, <bus;
	var <synth, synthdef;

	var uvw, font;
	var frdb, frpresets;
	var bypassButton;
	var selected;
	var tvw, tvwViews;
	var puMenu, puButtons, puFileButtons;

	*initClass {
		prefsFile = Platform.userConfigDir +/+ "eq-prefs.dat";
	}

	*new { |numChannels, server, bus, target|
		if (\TabbedView.asClass.notNil) {
			^super.new.init(numChannels, server, bus, target);
		} { "ChannelEQ requires the TabbedView Quark".error };
	}

	play {
		"play".postln;
		server.waitForBoot {
			if (target.notNil) {
				// using MixerChannel
				synth = target.playfx(\param_beq, [\eq_controls, this.toControl]);
			} {
				// using raw Bus
				synth = Synth.tail(server, \param_beq, [\out, bus, \eq_controls, this.toControl]);
			};
			NodeWatcher.register(synth);
		};
	}
			
	free {
		synth.set(\gate, 0);
	}

	ar { |input|
		var frdb;
		frdb = this.fromControl(Control.names([\eq_controls]).kr(0!15));
				
		input = BLowShelf.ar(input, *frdb[0][[0,2,1]].lag(0.1));
		input = BPeakEQ.ar(input, *frdb[1][[0,2,1]].lag(0.1));
		input = BPeakEQ.ar(input, *frdb[2][[0,2,1]].lag(0.1));
		input = BPeakEQ.ar(input, *frdb[3][[0,2,1]].lag(0.1));
		input = BHiShelf.ar(input, *frdb[4][[0,2,1]].lag(0.1));
		input = RemoveBadValues.ar(input);
		
		^input;
	}
	
	fromControl { |controls|
		^controls.clump(3).collect({ |item|
			[(item[0] + 1000.cpsmidi).midicps, item[1], 10**item[2]]
		});
	}

	toControl {
		^frdb.collect({ |item, i|
			[item[0].cpsmidi - 1000.cpsmidi, item[1], item[2].log10]
		}).flat;
	}

	sendCurrent {
		if (synth.notNil) {
			synth.setn(\eq_controls, this.toControl);
		}
	}

	tvwRefresh { 
		frdb.do({ |item, i| 
			item.do({ |subitem, ii| 
				tvwViews[i][ii].value = subitem;
			});
		});
	}

	puMenuCheck	{
		var index;
		index = frpresets.clump(2).detectIndex({ |item| item[1] == frdb });
		if (index.notNil) {
			puMenu.value = index;
			puButtons[0].enabled_(false);
			if (frpresets[index * 2].asString[..1] == "x_") {
				puButtons[1].enabled_(false);
			} {
				puButtons[1].enabled_(true);
			};
		} {
			puMenu.value = (frpresets.size/2) + 1; 
			puButtons[1].enabled_(false);  
			puButtons[0].enabled_(true);
		};
	}

	puMenuCreateItems {
		var items;
		items = [];
		frpresets.pairsDo({ |key, value|
			if (key.asString[..1] == "x_") {
				items = items.add(key.asString[2..]);
			} {
				items = items.add(key.asString);
			};
		});
		items = items ++ ["-", "(custom)"];
		puMenu.items = items;
	}
	
	doOnServerTree {
		if (synth.isPlaying) { { this.play; }.defer(0.05); };
	}

	init { |argNumChannels, argServer, argBus, argTarget|
		// Hack to avoid sclang from yelling at us for using the class name itself
		var mixerChannel = \MixerChannel.asClass;

		if (argTarget.notNil
			and: mixerChannel.notNil
			and: { argTarget.isKindOf(mixerChannel) }) {
			// MixerChannel in use
			target = argTarget;
			server = target.server;
			numChannels = target.inChannels;
		} {
			if (argBus.isNil) {
				// If no bus is provided, default to zero
				bus = 0;
				// Server
				server = argServer ? Server.default;
				// Number of channels set to 2
				numChannels = argNumChannels ? 2;
			} {
				// If a bus is provided, use it
				bus = argBus.asBus;
				// and its server
				server = argServer ? bus.server;
				// and its number of channels (unless overridden)
				numChannels = argNumChannels ? if (argBus.isNumber) { 2 } { bus.numChannels };
			};
		};

		window = Window.new(
			if (target.isNil) {
				"ChannelEQ on bus % (% channels)".format(bus, numChannels)
			} {
				"ChannelEQ on '%' (% channels)".format(target.name, numChannels)
			},
			Rect(299, 130, 305, 220), true
		).front; 
				
		window.view.decorator = FlowLayout(window.view.bounds, 10@10, 4@0);
		
		uvw = UserView(window, 
			window.view.bounds.insetBy(10,10)
				.height_(window.view.bounds.height - 80) 
			).resize_(5);
			
		font = Font(Font.defaultSansFace, 10);
		
		// uvw.relativeOrigin = false;
		
		uvw.focusColor = Color.clear;
		
		frdb = [[100,0,1], [250,0,1], [1000,0,1], [3500,0,1], [6000,0,1]];
		
		frpresets = [// x_ = cannot delete or modify 
			'x_flat', [[100, 0, 1], [250, 0, 1], [1000, 0, 1], [3500, 0, 1], 
				[6000, 0, 1]], 
			'x_loudness', [[78.0, 7.5, 0.65], [250, 0, 1], [890.0, -9.5, 3.55], 
				[2800.0, 3.5, 1.54], [7400.0, 7.0, 1.0]], 
			'x_telephone', [[600.0, -22.0, 0.7], [250, 0, 1], [1200.0, -2.0, 0.5],
				[1800.0, 1.0, 0.5], [4000.0, -22.0, 0.7]]
			];
			
		//frdb = frpresets[1].deepCopy;
		
		selected = -1;
		
		tvw = TabbedView(window, 
			window.view.bounds.insetBy(10,10).height_(35).top_(200),
			["low shelf", "peak 1", "peak 2", "peak 3", "high shelf"],
			{ |i| Color.hsv(i.linlin(0,5,0,1), 0.75, 0.5).alpha_(0.25); }!5)
				.font_(font)
				.resize_(8)
				.tabPosition_(\bottom);
				
		tvw.focusActions = { |i| { selected = i; uvw.refresh;  }; }!5;
		
		tvwViews = [];
		
		window.view.decorator.shift(0,8);
		
		tvw.views.do({ |view,i| 
			var vw_array = [];
			
			view.decorator = FlowLayout(view.bounds.moveTo(0,0)); 
			
			StaticText(view, 35@14).font_(font).align_(\right).string_("freq:");
			vw_array = vw_array.add(
				RoundNumberBox(view, 40@14).font_(font).value_(frdb[i][0])
					.clipLo_(20).clipHi_(22000)
					.action_({ |vw|
						frdb[i][0] = vw.value;
						this.sendCurrent;
						uvw.refresh;
						this.puMenuCheck;
						}) );
			
			StaticText(view, 25@14).font_(font).align_(\right).string_("db:");
			vw_array = vw_array.add(
				RoundNumberBox(view, 40@14).font_(font).value_(frdb[i][1])
					.clipLo_(-36).clipHi_(36)
					.action_({ |vw|
						frdb[i][1] = vw.value;
						this.sendCurrent;
						uvw.refresh;
						this.puMenuCheck;
						}) );
			
			StaticText(view, 25@14).font_(font).align_(\right)
				.string_((0: "rs:", 4:"rs:")[i] ? "rq" );
			vw_array = vw_array.add(
				RoundNumberBox(view, 40@14).font_(font).value_(frdb[i][2])
					.step_(0.1).clipLo_(if ([0,4].includes(i)) { 0.6 } {0.01}).clipHi_(10)
					.action_({ |vw|
						frdb[i][2] = vw.value;
						this.sendCurrent;
						uvw.refresh;
						this.puMenuCheck;
						}) 
						);
			
			tvwViews = tvwViews.add(vw_array);
			
			});
			
		bypassButton = RoundButton.new(window, 17@17)
				.extrude_(true).border_(1) //.font_(font)
				.states_([
					['power', Color.gray(0.2), Color.white(0.75).alpha_(0.25)],
					['power', Color.red(0.8), Color.white(0.75).alpha_(0.25)]])
				.value_(1)
				.action_({ |bt| switch(bt.value,
					1, { this.play },
					0, { this.free });
					})
				.resize_(7);
			
		puMenu = PopUpMenu.new(window, 100@16)
			.font_(font).canFocus_(false)
			.resize_(7);
			
		if (GUI.id == \swing) {
			puMenu.bounds = puMenu.bounds.insetBy(-3,-3).moveBy(0, 1)
		};
			
		//this.puMenuCheck;
		puButtons = [
			RoundButton.new(window, 16@16)
				.border_(1)
				.states_([['+']])
				.resize_(7)
				,
			RoundButton.new(window, 16@16)
				.border_(1)
				.resize_(7)
				.states_([['-']]),	
			];
		
		puFileButtons = [
			RoundButton.new(window, 55@16)
				.extrude_(true).border_(1).font_(font)
				.states_([["save", Color.black, Color.red(0.75).alpha_(0.25)]])
				.resize_(7),
			RoundButton.new(window,  55@16)
				.extrude_(true).border_(1).font_(font)
				.states_([["revert", Color.black, Color.green(0.75).alpha_(0.25)]])
				.resize_(7)
		];
		
		puFileButtons[0].action_({
			File.use(prefsFile, "w", { |f| 
				f.write((current: frdb, presets: frpresets).asCompileString);
			});
		});
		
		puFileButtons[1].action_({
			var contents;
			if (File.exists(prefsFile)) {
				File.use(prefsFile, "r", { |f| 
					contents = f.readAllString.interpret;
					//contents.postln;
					frdb = contents[\current];
					frpresets = contents[\presets];
					this.sendCurrent;
					this.puMenuCreateItems;
					this.puMenuCheck;
					uvw.refresh;
					this.tvwRefresh;
				});
			};
		});

		this.puMenuCreateItems;
	
		puMenu.action = { |pu|
			frdb = frpresets[(pu.value * 2) + 1].deepCopy;
			//frdb.postln;
			this.sendCurrent;
			uvw.refresh;
			this.tvwRefresh;
			if (frpresets[pu.value * 2].asString[..1] == "x_") {
				puButtons[0].enabled_(false); 
				puButtons[1].enabled_(false);
			} {
				puButtons[0].enabled_(false);
				puButtons[1].enabled_(true);
			};
		};
			
		puButtons[0].action = { |bt|
			var testPreset, addPreset, replacePreset;
			
			testPreset = { |name = "user"|
				var index, xnames, clpresets;
				name = name.asSymbol;
				index = frpresets.clump(2)
					.detectIndex({ |item| item[0] == name.asSymbol });
				xnames = frpresets.clump(2)
					.select({ |item| item[0].asString[..1] == "x_" })
					.collect({ |item| item[0].asString[2..].asSymbol });
				if (index.isNil) {
					if (xnames.includes(name).not) {
						addPreset.value(name);
					} { 
						SCAlert("EQ preset '%' cannot be overwritten.\nPlease choose a different name"
								.format(name), ["ok"]);
					};
				} {
					SCAlert("EQ preset '%' already exists.\nDo you want to overwrite it?"
							.format(name), ["cancel","ok"], 
							[{}, { replacePreset.value(name, index) }]); 
				};
			};
				
			addPreset = { |name = "user"|
				frpresets = frpresets ++ [name.asSymbol, frdb.deepCopy];
				this.puMenuCreateItems;
				this.puMenuCheck;
			};
				
			replacePreset = { |name = "x_default", index = 0|
				frpresets[index * 2] = name.asSymbol;
				frpresets[(index * 2)+1] = frdb.deepCopy;
				this.puMenuCreateItems;
				this.puMenuCheck;
			};
			
			SCRequestString( "user", "Enter a short name for the new preset",
				{ |str| testPreset.value(str); });
		};
		
		puButtons[1].action = { |bt|
			 SCAlert("Are you sure you want to\ndelete preset '%'"
						.format(puMenu.items[puMenu.value]), ["cancel","ok"], 
						[{}, {
						frpresets.removeAt( puMenu.value * 2);
						frpresets.removeAt( puMenu.value * 2);
						this.puMenuCreateItems;
						this.puMenuCheck;
					}]); 
		};
		
		this.puMenuCheck;
		
		uvw.mouseDownAction = { |vw, x, y, mod|
			var bounds;
			var pt;
			var min = 20, max = 22050, range = 24;
			
			bounds = vw.bounds.moveTo(0, 0);
			//pt = (x@y) - (bounds.leftTop);
			pt = (x@y);
			
			selected =  frdb.detectIndex({ |array|
				((array[0].explin(min, max, 0, bounds.width))@(array[1].linlin(range.neg, range, bounds.height, 0, \none)))
					.dist(pt) <= 5;
			}) ? -1;
				
			if (selected != -1) { tvw.focus(selected) };
			vw.refresh;
		};
			
		uvw.mouseMoveAction = { |vw, x, y, mod|
			var bounds;
			var pt;
			var min = 20, max = 22050, range = 24;
			
			bounds = vw.bounds.moveTo(0,0);
			//pt = (x@y) - (bounds.leftTop);
			pt = (x@y);
			
			if (selected != -1)
				{
				case { ModKey(mod).alt }
					{ 
					if ( ModKey(mod).shift)
						{
					frdb[selected] = frdb[selected][[0,1]] 
						++ [y.linexp(bounds.height, 0, 0.1, 10, \none).nearestInList(
							if ([0,4].includes(selected)) 
								{[0.6,1,2.5,5,10]} 
								{[0.1,0.25,0.5,1,2.5,5,10]}
								
							)];
						}
						{
					frdb[selected] = frdb[selected][[0,1]] 
						++ [y.linexp(bounds.height, 0, 0.1, 10, \none).clip(
								 if ([0,4].includes(selected)) { 0.6 } {0.1},
								 	10).round(0.01)];
						};
					tvwViews[selected][2].value = frdb[selected][2];
						 }
					{ ModKey(mod).shift }
					{
				frdb[selected] = [
					pt.x.linexp(0, bounds.width, min, max)
						.nearestInList([25,50,75,100,250,500,750,1000,2500,5000,7500,10000]),
					pt.y.linlin(0, bounds.height, range, range.neg, \none)
						.clip2(range).round(6),
					frdb[selected][2] 
					];
				tvwViews[selected][0].value = frdb[selected][0];
				tvwViews[selected][1].value = frdb[selected][1];	
					}
					{ true }
					{
				frdb[selected] = [
					pt.x.linexp(0, bounds.width, min, max).clip(20,20000).round(1),
					pt.y.linlin(0, bounds.height, range, range.neg, \none).clip2(range)
						.round(0.25),
					frdb[selected][2] 
					];	
				tvwViews[selected][0].value = frdb[selected][0];
				tvwViews[selected][1].value = frdb[selected][1];		};
			this.sendCurrent;
			vw.refresh;
			this.puMenuCheck;
				};
		
			};
		
		uvw.drawFunc = { |vw|
			var freqs, svals, values, bounds, zeroline;
			var freq = 1200, rq = 0.5, db = 12;
			var min = 20, max = 22050, range = 24;
			var vlines = [100,1000,10000];
			var dimvlines = [25,50,75, 250,500,750, 2500,5000,7500];
			var hlines = [-18,-12,-6,6,12,18];
			var pt, strOffset = 11;
			
			if (GUI.id === 'swing') { strOffset = 14 };
			
			bounds = vw.bounds.moveTo(0,0);
			
			#freq,db,rq = frdb[0] ? [freq, db, rq];
			
			freqs = ({|i| i } ! (bounds.width+1));
			freqs = freqs.linexp(0, bounds.width, min, max);
			
			values = [
				BLowShelf.magResponse(freqs, 44100, frdb[0][0], frdb[0][2], 
					frdb[0][1]),
				BPeakEQ.magResponse(freqs, 44100, frdb[1][0], frdb[1][2], 
					frdb[1][1]),
				BPeakEQ.magResponse(freqs, 44100, frdb[2][0], frdb[2][2], 
					frdb[2][1]),
				BPeakEQ.magResponse(freqs, 44100, frdb[3][0], frdb[3][2], 
					frdb[3][1]),
				BHiShelf.magResponse(freqs, 44100, frdb[4][0], frdb[4][2], 
					frdb[4][1])
					].ampdb.max(-200).min(200);
			
			zeroline = 0.linlin(range.neg,range, bounds.height, 0, \none);
			
			svals = values.sum.linlin(range.neg,range, bounds.height, 0, \none);
			values = values.linlin(range.neg,range, bounds.height, 0, \none);
			
			vlines = vlines.explin(min, max, 0, bounds.width);
			dimvlines = dimvlines.explin(min, max, 0, bounds.width);
			
			pt = frdb.collect({ |array|
				(array[0].explin(min, max, 0, bounds.width))
				@
				(array[1].linlin(range.neg,range,bounds.height,0,\none));
				});

				Pen.color_(Color.white.alpha_(0.25));
				Pen.roundedRect(bounds, [6,6,0,0]).fill;
				
				Pen.color = Color.gray(0.2).alpha_(0.5);
				//Pen.strokeRect(bounds.insetBy(-1,-1));
				
				//Pen.addRect(bounds).clip;
				Pen.roundedRect(bounds.insetBy(0,0), [6,6,0,0]).clip;
				
				Pen.color = Color.gray(0.2).alpha_(0.125);
				
				hlines.do({ |hline,i|
					hline = hline.linlin(range.neg,range, bounds.height, 0, \none);
					Pen.line(0@hline, bounds.width@hline)
					});
				dimvlines.do({ |vline,i|
					Pen.line(vline@0, vline@bounds.height);
					});
				Pen.stroke;
			
				Pen.color = Color.gray(0.2).alpha_(0.5);
				vlines.do({ |vline,i|
					Pen.line(vline@0, vline@bounds.height);
					});
				Pen.line(0@zeroline, bounds.width@zeroline).stroke;
				
				/*
				Pen.color = Color.white.alpha_(0.5);
				Pen.fillRect(Rect(33, 0, 206, 14));
				*/
				
				Pen.font = font;
				
				Pen.color = Color.gray(0.2).alpha_(0.5);
				hlines.do({ |hline|
					Pen.stringAtPoint(hline.asString ++ "dB", 
						3@(hline.linlin(range.neg,range, bounds.height, 0, \none) 
							- strOffset));
					});
				vlines.do({ |vline,i|
					Pen.stringAtPoint(["100Hz", "1KHz", "10KHz"][i], 
						(vline+2)@(bounds.height - (strOffset + 1)));
					});
				
				//Pen.roundedRect(bounds.insetBy(0.5,0.5), [5,5,0,0]).stroke;
				
				/*
				if (selected != -1)
					{ Pen.stringAtPoint(
						["low shelf: %hz, %dB, rs=%",
						  "peak 1: %hz, %dB, rq=%",
						  "peak 2: %hz, %dB, rq=%",
						  "peak 3: %hz, %dB, rq=%",
						  "hi shelf: %hz, %dB, rs=%"
						][selected].format(
							frdb[selected][0],
							frdb[selected][1],
							frdb[selected][2]
							),
						35@1);
					 }
					 { Pen.stringAtPoint("shift: snap, alt: rq", 35@1); };
				*/
						
				values.do({ |svals,i|
					var color;
					color = Color.hsv(
						i.linlin(0,values.size,0,1), 
						0.75, 0.5).alpha_(if (selected == i) { 0.75 } { 0.25 });
					Pen.color = color;
					Pen.moveTo(0@(svals[0]));
					svals[1..].do({ |val, i|
						Pen.lineTo((i+1)@val);
						});
					Pen.lineTo(bounds.width@(bounds.height/2));
					Pen.lineTo(0@(bounds.height/2));
					Pen.lineTo(0@(svals[0]));
					Pen.fill;
					
					Pen.addArc(pt[i], 5, 0, 2pi);
					
					Pen.color = color.alpha_(0.75);
					Pen.stroke;
		
					});
				
				Pen.color = Color.blue(0.5);
				Pen.moveTo(0@(svals[0]));
				svals[1..].do({ |val, i|
					Pen.lineTo((i + 1)@val);
					});
				Pen.stroke;
				
				Pen.extrudedRect(bounds, [6,6,0,0], 1, inverse: true);

		};

		puFileButtons[1].action.value; // revert
		window.refresh;
		 
		//uvw.refreshInRect(uvw.bounds.insetBy(-2,-2));
			
		window.onClose_ { if (synth.isPlaying != false) { this.free; }; };
			
		synthdef = SynthDef("param_beq", { |out = 0, gate = 1, fadeTime = 0.05, doneAction = 2|
			var frdb, input, env;
			env = EnvGen.kr(Env.asr(fadeTime, 1, fadeTime), gate, doneAction: doneAction);
			input = In.ar(out, numChannels);
			input = this.ar(input);
			XOut.ar(out, env, input);
		}).store;
		
		this.play;
		
	}


}