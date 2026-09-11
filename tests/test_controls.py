"""Exercise the minimal Lua script with a mocked norns host."""
from pathlib import Path
from lupa import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(r'''
calls, polls, values, actions, definitions = {}, {}, {}, {}, {}
engine = setmetatable({}, {__index=function(_,name)
  return function(...) table.insert(calls,{name,...}) end
end})
package.preload.controlspec=function() return {new=function(a,b,w,s,d,u)
  return {min=a,max=b,default=d,units=u} end} end
package.preload.util=function() return {clamp=function(v,a,b)
  return math.max(a,math.min(v,b)) end} end
params={
  add_separator=function() end,
  add_control=function(_,id,name,spec)
    values[id]=spec.default; definitions[id]={name=name,spec=spec}
  end,
  set_action=function(_,id,fn) actions[id]=fn end,
  get=function(_,id) return values[id] end,
  set=function(_,id,v) values[id]=v; if actions[id] then actions[id](v) end end,
  delta=function(_,id,d)
    local s=definitions[id].spec
    values[id]=math.max(s.min,math.min(s.max,values[id]+d*0.01))
    if actions[id] then actions[id](values[id]) end
  end,
  lookup_param=function(_,id) return definitions[id] end,
  string=function(_,id)
    local p=definitions[id]
    if p.formatter then return p.formatter({get=function() return values[id] end}) end
    return tostring(values[id])
  end
}
poll={set=function(name)
  local p={start=function() end,stop=function(self) self.stopped=true end}
  polls[name]=p; return p
end}
metro={init=function() return {start=function() end,stop=function() end} end}
clock_jobs={}
clock={
  run=function(fn) table.insert(clock_jobs,fn); return #clock_jobs end,
  cancel=function(id) clock_jobs[id]=nil end,
  sleep=function() end
}
function run_clocks()
  local jobs=clock_jobs; clock_jobs={}
  for _,fn in pairs(jobs) do fn() end
end
screen=setmetatable({}, {__index=function() return function() end end})
function count(name)
  local n=0; for _,c in ipairs(calls) do if c[1]==name then n=n+1 end end
  return n
end
function last(name)
  for i=#calls,1,-1 do if calls[i][1]==name then return calls[i] end end
end
''')
lua.execute((ROOT / "grainloom.lua").read_text())
lua.execute(r'''
init()
assert(engine.name=='Grainloom')
assert(last('feedback')[2]==0.72)
assert(last('rate')[2]==1)
assert(last('mix')[2]==0.8)
assert(last('sample_gain')[2]==1.5)
assert(last('gain')[2]==0.75)
assert(last('slice_size')[2]==0.2)
assert(last('slice_density')[2]==5)
assert(last('slice_speed')[2]==1.5)
assert(last('slice_reverse')[2]==0.35)
assert(last('slice_mix')[2]==0.5)
assert(last('liveLoop')[2]==1 and last('liveLoop')[3]==2.5)
assert(definitions.grainloom_mix.formatter({get=function() return 0 end})=='10:0')
assert(definitions.grainloom_mix.formatter({get=function() return 1 end})=='0:10')

-- Keys are ignored while the initial Buffer is allocating.
key(2,1); assert(count('writing')==0)
polls.grainloom_state.callback(14)
key(2,1); assert(last('writing')[2]==0)
polls.grainloom_state.callback(21)
key(2,1); assert(last('writing')[2]==1)
key(3,1); assert(last('playing')[2]==0)
key(3,1); assert(last('playing')[2]==1)

-- Recording-time edits debounce and restart once at the final length.
params:set('grainloom_capture_length',4)
params:set('grainloom_capture_length',6)
run_clocks()
assert(count('liveLoop')==2)
assert(last('liveLoop')[2]==1 and last('liveLoop')[3]==6)

-- All five pages dispatch valid engine controls.
polls.grainloom_state.callback(33)
polls.grainloom_state.callback(44)
for i=1,5 do enc(1,1); enc(2,1); enc(3,-1); redraw() end

cleanup()
assert(last('liveLoop')[2]==0)
assert(polls.grainloom_state.stopped and polls.grainloom_seconds.stopped)
''')
print("PASS: minimal loop controls, freeze/on-off keys, resize debounce, cleanup")
