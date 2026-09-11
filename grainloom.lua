-- grainloom: a minimal continuous feedback loop machine
-- SPDX-License-Identifier: MIT
engine.name = 'Grainloom'

local cs = require 'controlspec'
local util = require 'util'

local function pid(id) return 'grainloom_'..id end
local function pget(id) return params:get(pid(id)) end

local pages = {
  {'LOOP', 'capture_length', 'feedback'},
  {'TAPE', 'rate', 'mix'},
  {'SLICE', 'slice_size', 'slice_density'},
  {'SLICE PLAY', 'slice_speed', 'slice_reverse'},
  {'LEVEL', 'slice_mix', 'sample_gain'}
}

local page = 1
local state = 0 -- 0 empty, 1 write frozen, 3 allocating, 4 recording
local seconds = 0
local revision = 0
local pending = true
local writing = true
local playing = true
local message = 'starting loop...'
local state_poll, time_poll, refresh, resize_clock

local function control(id, name, min, max, warp, default, units, action)
  params:add_control(pid(id), name,
    cs.new(min, max, warp, 0, default, units or ''))
  if action then params:set_action(pid(id), action) end
end

local function restart_loop(length)
  pending = true
  writing = true
  playing = true
  message = 'resizing loop...'
  engine.liveLoop(1, length)
end

function init()
  params:add_separator('grainloom')

  control('capture_length', 'recording time', 0.1, 30, 'lin', 2.5, 's')
  control('feedback', 'feedback', 0, 0.98, 'lin', 0.72, '',
    function(v) engine.feedback(v) end)
  control('rate', 'tape speed', -2, 2, 'lin', 1, 'x',
    function(v) engine.rate(v) end)
  control('mix', 'input / sample mix', 0, 1, 'lin', 0.8, '',
    function(v) engine.mix(v) end)
  params:lookup_param(pid('mix')).formatter = function(param)
    local sample = math.floor(param:get()*10+0.5)
    return (10-sample)..':'..sample
  end
  control('sample_gain', 'sample level', 0.25, 4, 'exp', 1.5, 'x',
    function(v) engine.sample_gain(v) end)
  control('slice_size', 'slice size', 0.06, 0.5, 'exp', 0.2, 's',
    function(v) engine.slice_size(v) end)
  control('slice_density', 'slice density', 1, 8, 'exp', 5, 'Hz',
    function(v) engine.slice_density(v) end)
  control('slice_speed', 'random speed max', 0.5, 2, 'lin', 1.5, 'x',
    function(v) engine.slice_speed(v) end)
  control('slice_reverse', 'reverse chance', 0, 1, 'lin', 0.35, '',
    function(v) engine.slice_reverse(v) end)
  control('slice_mix', 'tape / slice mix', 0, 1, 'lin', 0.5, '',
    function(v) engine.slice_mix(v) end)
  control('gain', 'output level', 0, 1, 'lin', 0.75, '',
    function(v) engine.gain(v) end)

  params:set_action(pid('capture_length'), function(v)
    if resize_clock then clock.cancel(resize_clock) end
    resize_clock = clock.run(function()
      clock.sleep(0.4)
      resize_clock = nil
      if state ~= 3 then restart_loop(v) end
    end)
  end)

  -- Initialize only Grainloom controls; never bang unrelated system params.
  engine.feedback(pget('feedback'))
  engine.rate(pget('rate'))
  engine.mix(pget('mix'))
  engine.sample_gain(pget('sample_gain'))
  engine.slice_size(pget('slice_size'))
  engine.slice_density(pget('slice_density'))
  engine.slice_speed(pget('slice_speed'))
  engine.slice_reverse(pget('slice_reverse'))
  engine.slice_mix(pget('slice_mix'))
  engine.gain(pget('gain'))

  state_poll = poll.set('grainloom_state')
  state_poll.time = 0.1
  state_poll.callback = function(v)
    local rev = math.floor(v/10)
    state = v%10
    if rev > revision then
      pending = state == 3
      if state == 4 then
        writing = true
        message = 'recording + replaying'
      elseif state == 1 then
        writing = false
        message = 'freeze on'
      elseif state == 0 then
        message = 'loop stopped'
      end
    end
    revision = rev
  end

  time_poll = poll.set('grainloom_seconds')
  time_poll.time = 0.1
  time_poll.callback = function(v) seconds = v end
  state_poll:start()
  time_poll:start()

  engine.liveLoop(1, pget('capture_length'))
  refresh = metro.init(function() redraw() end, 1/15)
  refresh:start()
end

function enc(n, d)
  if n == 1 then
    page = util.clamp(page+d, 1, #pages)
  elseif n == 2 or n == 3 then
    params:delta(pid(pages[page][n]), d)
  end
  redraw()
end

function key(n, z)
  if z == 0 or pending then return end
  if n == 2 then
    writing = not writing
    engine.writing(writing and 1 or 0)
    message = writing and 'freeze off' or 'freeze on'
  elseif n == 3 then
    playing = not playing
    engine.playing(playing and 1 or 0)
    message = playing and 'sample on' or 'sample off'
  end
  redraw()
end

function redraw()
  screen.clear()
  screen.aa(1)
  screen.font_size(8)
  screen.level(15)
  screen.move(0, 9)
  screen.text('grainloom')
  screen.move(128, 9)
  local status = pending and 'WAIT' or (writing and 'LOOP' or 'FREEZE')
  if not playing then status = 'OFF' end
  screen.text_right(status..string.format(' %.1fs', seconds))

  screen.level(5)
  screen.move(0, 22)
  screen.text(page..'/'..#pages..' '..pages[page][1])
  for i=2,3 do
    local id = pages[page][i]
    screen.level(12)
    screen.move(0, 20+i*10)
    screen.text(params:lookup_param(pid(id)).name)
    screen.move(128, 20+i*10)
    screen.text_right(params:string(pid(id)))
  end

  screen.level(5)
  screen.move(0, 63)
  screen.text('K2 freeze     K3 on/off')
  screen.update()
end

function cleanup()
  if resize_clock then clock.cancel(resize_clock); resize_clock = nil end
  if refresh then refresh:stop() end
  if state_poll then state_poll:stop() end
  if time_poll then time_poll:stop() end
  engine.liveLoop(0, pget('capture_length'))
end
