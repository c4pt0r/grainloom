-- grainloom: loop / grain / decay
-- v0.1.0 experimental | SPDX-License-Identifier: MIT
-- E1 page, E2/E3 edit; K2 record/stop, K3 freeze
-- hold K1: K2 load, K3 play/pause
engine.name = 'Grainloom'

local cs = require 'controlspec'
local fileselect = require 'fileselect'
local util = require 'util'
local pages = {
  {'GRAIN', 'size', 'density'}, {'SCAN', 'position', 'scan'},
  {'TAPE', 'rate', 'blend'}, {'SCATTER', 'spray', 'glitch'},
  {'PATINA', 'crush', 'tone'}, {'SPACE', 'reverb', 'room'},
  {'TAPE LOSS', 'loss', 'dropout'}, {'INSTABILITY', 'wow', 'flutter'},
  {'LEVEL', 'gain', 'capture_length'}
}
local page, shift, frozen, playing = 1, false, false, false
local state, seconds, message = 0, 0, 'K2 record / K1+K2 load'
local state_poll, time_poll, refresh
local selecting, pending = false, false
local generations, bouncing = 0, false
local revision = 0

local function resample()
  if state ~= 1 or pending then return end
  pending = true; bouncing = true
  message = 'printing next generation'
  engine.capture(math.max(0.1,seconds),1)
end

local function control(id, name, min, max, warp, default, units)
  params:add_control(id, name, cs.new(min,max,warp,0,default,units or ''))
  if id ~= 'capture_length' then
    params:set_action(id, function(v) engine[id](v) end)
  end
end

local function load_sample()
  if state == 2 or state == 3 or pending then return end
  selecting = true
  shift = false
  fileselect.enter(_path.audio, function(path)
    selecting = false
    if path == 'cancel' then return end
    local channels, frames, sr = audio.file_info(path)
    if not channels or channels < 1 or not frames or frames < 2 or not sr or sr <= 0 then
      message = 'cannot read audio'; return
    end
    pending = true
    generations = 0
    message = 'loading...'
    engine.read(path)
  end, 'audio')
end

function init()
  params:add_separator('grainloom')
  control('size','grain size',0.02,0.4,'exp',0.12,'s')
  control('density','density',2,40,'exp',12,'Hz')
  control('position','position',0,1,'lin',0)
  control('scan','scan speed',-2,2,'lin',1)
  control('rate','tape / grain rate',-2,2,'lin',1)
  control('blend','grain blend',0,1,'lin',0.7)
  control('spray','position spray',0,0.25,'lin',0.02)
  control('glitch','stutter chance',0,1,'lin',0)
  control('crush','lo-fi',0,1,'lin',0)
  control('tone','low-pass',200,18000,'exp',10000,'Hz')
  control('reverb','reverb mix',0,1,'lin',0.25)
  control('room','reverb room',0,0.98,'lin',0.8)
  control('gain','output',0,1,'lin',0.65)
  control('loss','generation loss',0,1,'lin',0.2)
  control('wow','wow',0,1,'lin',0.15)
  control('flutter','flutter',0,1,'lin',0.1)
  control('dropout','dropout',0,1,'lin',0)
  control('capture_length','max recording',0.1,30,'lin',8,'s')
  params:add_trigger('load_sample','load sample (first 30s)')
  params:set_action('load_sample',load_sample)
  params:add_trigger('resample','print next generation')
  params:set_action('resample',resample)
  -- Bang only our controls: do not invoke unrelated system parameter actions.
  for _,p in ipairs(pages) do
    for i=2,3 do
      if p[i] ~= 'capture_length' then engine[p[i]](params:get(p[i])) end
    end
  end
  state_poll = poll.set('grainloom_state')
  state_poll.time = 0.1
  state_poll.callback = function(v)
    local rev = math.floor(v/10)
    local status = v%10
    state = status == 9 and -1 or status
    if state == 2 then pending = false end
    if state == -1 then
      pending = false; playing = false; bouncing = false; message = 'load failed'
    end
    if rev > revision and state == 1 then
      pending = false
      playing = true; message = 'sample ready'
      if bouncing then generations = generations+1; bouncing = false end
    end
    revision = rev
  end
  time_poll = poll.set('grainloom_seconds')
  time_poll.time = 0.1
  time_poll.callback = function(v) seconds = v end
  state_poll:start(); time_poll:start()
  refresh = metro.init(function() if not selecting then redraw() end end, 1/15)
  refresh:start()
end

function enc(n,d)
  if n == 1 then page = util.clamp(page+d,1,#pages)
  elseif n == 2 or n == 3 then params:delta(pages[page][n],d) end
  redraw()
end

function key(n,z)
  if n == 1 then shift = z == 1; return end
  if z == 0 then return end
  if shift then
    if n == 2 then load_sample()
    elseif n == 3 and state == 1 and not pending then
      playing = not playing; engine.playing(playing and 1 or 0)
    end
  elseif n == 2 then
    if pending or state == 3 then return end
    if state == 2 then
      engine.stopCapture(); pending = true
    else
      engine.freeze(0); frozen = false
      pending = true; playing = false
      message = 'preparing recording...'
      generations = 0
      engine.capture(params:get('capture_length'),0)
    end
  elseif n == 3 then
    frozen = not frozen; engine.freeze(frozen and 1 or 0)
  end
  if not selecting then redraw() end
end

function redraw()
  screen.clear(); screen.aa(1); screen.font_size(8)
  screen.level(15); screen.move(0,9); screen.text('grainloom')
  local status = state == 2 and 'REC' or ((pending or state == 3) and 'WAIT'
    or (playing and (frozen and 'HOLD' or 'PLAY') or 'STOP'))
  screen.move(128,9); screen.text_right(status..string.format(' %.1fs',seconds))
  screen.level(5); screen.move(0,22)
  screen.text(page..'/'..#pages..' '..pages[page][1]..'  g'..generations)
  for i=2,3 do
    local id = pages[page][i]
    screen.level(12); screen.move(0,20+i*10)
    screen.text(params:lookup_param(id).name)
    screen.move(128,20+i*10); screen.text_right(params:string(id))
  end
  screen.level(5); screen.move(0,63)
  screen.text(shift and 'K2 load   K3 play/pause'
    or (state == 0 and message or 'K2 rec/stop   K3 hold'))
  if state == -1 then
    screen.clear(); screen.level(15); screen.move(0,25)
    screen.text(message); screen.move(0,45); screen.text('K1+K2 load another file')
  end
  screen.update()
end

function cleanup()
  if refresh then refresh:stop() end
  if state_poll then state_poll:stop() end
  if time_poll then time_poll:stop() end
  engine.stopCapture()
  engine.playing(0)
end
