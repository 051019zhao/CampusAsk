<!-- src/App.vue -->
<template>
  <!-- 这个标签是路由的出口，没有它，任何路由页面都装载不进来！ -->
  <router-view />
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
body {
  margin: 0;
  padding: 0;
}
</style>