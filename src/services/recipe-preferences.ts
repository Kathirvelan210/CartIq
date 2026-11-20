export type RecipePreferences = {
  idea:string; cuisine:string; meal:string; diet:string; difficulty:string;
  minutes:string; budget:string; servings:string;
};

export function recipeRequest(p:RecipePreferences):string {
  const idea=p.idea.trim();
  if(!idea && ![p.cuisine,p.meal,p.diet,p.difficulty,p.minutes,p.budget].some(Boolean))
    throw Error('Enter a dish, an ingredient or a cooking preference.');
  if(!/^\d+$/.test(p.servings)||+p.servings<1||+p.servings>20)
    throw Error('Choose between 1 and 20 servings.');
  if(p.minutes && (!/^\d+$/.test(p.minutes)||+p.minutes<1||+p.minutes>480))
    throw Error('Choose a total cooking time between 1 and 480 minutes.');
  if(p.budget && (!/^\d{1,7}(?:\.\d{1,2})?$/.test(p.budget)||+p.budget<=0||+p.budget>1000000))
    throw Error('Enter a positive budget of up to ₹10,00,000.');
  const lines=[idea || 'Suggest a recipe', 'Servings: '+p.servings];
  for(const [name,value] of [['Cuisine',p.cuisine],['Meal',p.meal],['Diet',p.diet],['Difficulty',p.difficulty]])
    if(value)lines.push(name+': '+value.toLowerCase().replace(/_/g,' '));
  if(p.minutes)lines.push('Maximum total time: '+p.minutes+' minutes');
  if(p.budget)lines.push('Target budget: INR '+p.budget);
  const prompt=lines.join('\n');
  if(prompt.length>600)throw Error('Shorten your recipe request to fit the selected preferences.');
  return prompt;
}
