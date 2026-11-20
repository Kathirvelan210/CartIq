/** Space repeats across the week, giving each selected dish a turn before reusing one. */
export function scheduleWeek<T extends {id:string}>(ranked:readonly T[]):T[] {
  const choices=Array.from(new Map(ranked.map(entry=>[entry.id,entry])).values());
  if(choices.length<2)return choices;
  const uses=new Map(choices.map(entry=>[entry.id,0]));
  const days:T[]=[];
  for(let day=0;day<7;day++){
    const least=Math.min(...uses.values());
    const candidates=choices.filter(entry=>uses.get(entry.id)===least && entry.id!==days[day-1]?.id);
    // The previously chosen dish cannot be the sole least-used dish immediately after its turn.
    const next=candidates.find(entry=>entry.id===ranked[day]?.id) ?? candidates[0]!;
    days.push(next);
    uses.set(next.id,least+1);
  }
  return days;
}
