const groupByCategoryOld = (products, includeUnavailable) => {
  return products
    .filter(product => includeUnavailable || product.ativo)
    .reduce((groups, product) => {
      const category = product.categoria || "Cardapio";
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category).push(product);
      return groups;
    }, new Map());
};

const groupByCategoryNew = (products, includeUnavailable) => {
  return products
    .filter(product => includeUnavailable || product.ativo)
    .reduce((groups, product) => {
      const category = product.categoria || "Cardapio";
      let list = groups.get(category);
      if (!list) {
        list = [];
        groups.set(category, list);
      }
      list.push(product);
      return groups;
    }, new Map());
};

const generateProducts = (numProducts, numCategories) => {
  const products = [];
  for (let i = 0; i < numProducts; i++) {
    products.push({
      id: i,
      ativo: i % 10 !== 0,
      categoria: `Category ${i % numCategories}`,
    });
  }
  return products;
};

const products = generateProducts(100000, 100);

const runBenchmark = (name, fn, iterations = 100) => {
  const start = performance.now();
  for (let i = 0; i < iterations; i++) {
    fn(products, false);
  }
  const end = performance.now();
  console.log(`${name}: ${(end - start).toFixed(2)}ms for ${iterations} iterations`);
  return end - start;
};

// Warmup
for (let i = 0; i < 10; i++) {
  groupByCategoryOld(products, false);
  groupByCategoryNew(products, false);
}

const oldTime = runBenchmark('Old', groupByCategoryOld);
const newTime = runBenchmark('New', groupByCategoryNew);

console.log(`Improvement: ${((oldTime - newTime) / oldTime * 100).toFixed(2)}%`);
