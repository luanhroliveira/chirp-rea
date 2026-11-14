# Informações e Navegador

## Informações

Resumo do rádio aberto:

- fabricante, modelo, faixa de canais e modos;
- driver usado (nome da classe do CHIRP), velocidade da serial, tamanho da imagem;
- bandas de frequência suportadas;
- metadados gravados no `.img` (versão do programa que salvou, modelo, etc.).

O texto pode ser selecionado e copiado.

## Navegador

Ferramenta avançada, como a aba "Browser" do CHIRP: mostra a memória do rádio
exatamente como o driver a interpreta.

- À esquerda, a árvore da estrutura: `memory[0…999]` (canais), `vfo`, `settings`,
  `ani`, `pttid`, `bank_name`…
- Clique em `▸` para expandir. Campos simples mostram o valor (número, texto,
  frequência BCD, bits).
- À direita: caminho do campo, **endereço** na memória, tamanho e **dump
  hexadecimal** dos bytes.

Útil para conferir um valor que não aparece nas outras abas, comparar com outro
programa ou ajudar a portar um driver. O Navegador só lê; ele não altera nada.

Atenção: `memory[0]` é o **canal 1** (a contagem começa em zero, como no CHIRP).
